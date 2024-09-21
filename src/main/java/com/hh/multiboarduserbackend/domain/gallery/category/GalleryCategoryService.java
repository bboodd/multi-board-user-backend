package com.hh.multiboarduserbackend.domain.gallery.category;

import com.hh.multiboarduserbackend.common.dto.response.CategoryResponseDto;
import com.hh.multiboarduserbackend.common.vo.CategoryVo;
import com.hh.multiboarduserbackend.domain.free.category.FreeCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class GalleryCategoryService {

    private final GalleryCategoryRepository galleryCategoryRepository;

    /**
     * 카테고리 리스트 조회
     * @return - 리스트
     */
    public List<CategoryVo> findAll() {
        return galleryCategoryRepository.findAll();
    }
}

