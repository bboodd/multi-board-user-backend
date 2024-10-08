package com.hh.multiboarduserbackend.domain.post;

import com.hh.multiboarduserbackend.aop.AuthenticationContextHolder;
import com.hh.multiboarduserbackend.common.dto.SearchDto;
import com.hh.multiboarduserbackend.domain.file.request.FileRequestDto;
import com.hh.multiboarduserbackend.domain.file.response.FileResponseDto;
import com.hh.multiboarduserbackend.common.dto.PaginationDto;
import com.hh.multiboarduserbackend.common.dto.PagingAndListResponse;
import com.hh.multiboarduserbackend.common.response.Response;
import com.hh.multiboarduserbackend.utils.FileUtils;
import com.hh.multiboarduserbackend.utils.PaginationUtils;
import com.hh.multiboarduserbackend.domain.file.FileVo;
import com.hh.multiboarduserbackend.common.vo.SearchVo;
import com.hh.multiboarduserbackend.domain.board.BoardType;
import com.hh.multiboarduserbackend.domain.file.FileService;
import com.hh.multiboarduserbackend.domain.member.LoginMember;
import com.hh.multiboarduserbackend.domain.post.request.PostRequestDto;
import com.hh.multiboarduserbackend.domain.post.response.PostResponseDto;
import com.hh.multiboarduserbackend.exception.FileErrorCode;
import com.hh.multiboarduserbackend.exception.PostErrorCode;
import com.hh.multiboarduserbackend.mappers.FileMapper;
import com.hh.multiboarduserbackend.mappers.PostMapper;
import com.hh.multiboarduserbackend.mappers.SearchMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/api-board")
public class PostController {

    private final PostService postService;
    private final FileService fileService;

    private final FileUtils fileUtils;
    private final PaginationUtils paginationUtils;

    private final PostMapper postModelMapper = Mappers.getMapper(PostMapper.class);
    private final SearchMapper searchModelMapper = Mappers.getMapper(SearchMapper.class);
    private final FileMapper fileModelMapper = Mappers.getMapper(FileMapper.class);

    // 게시글 리스트 조회
    @GetMapping("/{boardType}/posts")
    public ResponseEntity<?> getPosts(@PathVariable String boardType, SearchDto searchDto) {

        PagingAndListResponse<PostResponseDto> pagingAndListResponse;
        Long typeId = BoardType.getTypeId(boardType);
        SearchVo voAsCount = searchModelMapper.toVoWithTypeId(searchDto, typeId);
        int count = postService.countAllBySearch(voAsCount);

        if(count >= 1) {
            PaginationDto paginationDto = paginationUtils.createPagination(count ,searchDto);

            SearchVo voAsSearch = searchModelMapper.toVoWithPaginationAndTypeId(searchDto, paginationDto, typeId);
            List<PostVo> postVoList = postService.findAllBySearch(voAsSearch);
            List<PostResponseDto> postResponseDtoList = postModelMapper.toDtoList(postVoList);

            pagingAndListResponse = new PagingAndListResponse<>(postResponseDtoList, paginationDto);
        } else {
            pagingAndListResponse = new PagingAndListResponse<>(Collections.emptyList(), null);
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.data(pagingAndListResponse));
    }

    // 게시글 조회
    @GetMapping("/{boardType}/posts/{postId}")
    public ResponseEntity<?> getPost(@PathVariable String boardType, @PathVariable Long postId) {

        postService.increaseViewCntById(postId);

        PostVo postVo = postService.findById(postId).orElseThrow(PostErrorCode.POST_NOT_FOUND::defaultException);
        PostResponseDto postResponseDto = postModelMapper.toDto(postVo);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.data(postResponseDto));
    }

    // 게시글 저장
    @LoginMember
    @PostMapping("/{boardType}/posts")
    public ResponseEntity<?> savePost(@PathVariable String boardType, @Valid PostRequestDto postRequestDto) {

        Long memberId = AuthenticationContextHolder.getContext();
        Long typeId = BoardType.getTypeId(boardType);
        PostVo postVo = postModelMapper.toVoWithMemberIdAndTypeId(postRequestDto, memberId, typeId);
        Long postId = postService.savePost(postVo);

        // 파일 업로드 및 저장
        if(boardType.equals("free") || boardType.equals("gallery")) {
            List<MultipartFile> fileList = postRequestDto.files();
            fileUploadAndSave(fileList, postId);

            if(boardType.equals("gallery")) {
                // 썸네일 업로드 및 저장
                thumbnailUploadAndSave(postId);
            }
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Response.message(postId + "번 게시글 등록완료"));
    }

    // 게시글 수정
    @LoginMember
    @PutMapping("/{boardType}/posts/{postId}")
    public ResponseEntity<?> updatePost(@PathVariable String boardType, @PathVariable Long postId, @Valid PostRequestDto postRequestDto) {

        final Long memberId = AuthenticationContextHolder.getContext();

        PostVo postVo = postModelMapper.toVoWithMemberId(postRequestDto, memberId);

        // 수정
        postService.updatePost(postVo);

        if(boardType.equals("free") || boardType.equals("gallery")){
            // 파일 삭제 from disk and db
            List<Long> removeFileIdList = postRequestDto.removeFileIds();
            fileDeleteDiskAndDb(removeFileIdList);
            // 파일 업로드 및 저장
            List<MultipartFile> fileList = postRequestDto.files();
            fileUploadAndSave(fileList, postId);

            if(boardType.equals("gallery")) {
                // 썸네일 업로드 및 저장
                thumbnailUploadAndSave(postId);
            }
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.message(postId + "번 게시글 수정 완료"));
    }

    // 게시글 삭제
    @LoginMember
    @DeleteMapping("/{boardType}/posts/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable String boardType, @PathVariable Long postId) {

        Long memberId = AuthenticationContextHolder.getContext();

        postService.deletePostById(postId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.message(postId + "번 게시글 삭제 완료"));
    }

    // 파일 업로드 및 저장 메서드
    private void fileUploadAndSave(List<MultipartFile> fileList, Long postId) {
        if(!CollectionUtils.isEmpty(fileList)) {
            // 업로드
            List<FileRequestDto> uploadFileList = fileUtils.uploadFiles(fileList);

            // 저장
            List<FileVo> fileVoList = fileModelMapper.toVoListWithPostId(uploadFileList, postId);

            fileService.saveFileList(fileVoList);
        }
    }

    // 썸네일 업로드 및 저장 메서드
    private void thumbnailUploadAndSave(Long postId) {
        // 썸네일이 없다면 업로드 및 저장
        if(!fileService.checkExistsThumbnail(postId)) {
            // db 에서 첫 파일 가져와서 변환
            FileVo firstFile = fileService.findFirstByPostId(postId).orElseThrow(FileErrorCode.FILE_NOT_FOUND::defaultException);
            FileVo uploadThumbnail = fileUtils.uploadThumbnail(firstFile);

            fileService.saveThumbnail(uploadThumbnail);
        }
    }

    // 파일 삭제 메서드
    private void fileDeleteDiskAndDb(List<Long> removeFileIdList) {
        if(!CollectionUtils.isEmpty(removeFileIdList)) {
            // 삭제할 파일 정보 조회
            List<FileVo> fileVoList = fileService.findAllByIds(removeFileIdList);
            List<FileResponseDto> deleteFileList = fileModelMapper.toDtoList(fileVoList);
            // 파일 삭제 from disk
            fileUtils.deleteFiles(deleteFileList);
            // 파일 삭제 from db
            fileService.deleteAllByIds(removeFileIdList);
        }
    }

}
