package com.hh.multiboarduserbackend.domain.member;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Mapper
public interface MemberRepository {

    void saveMember(MemberVo memberVo);

    MemberVo findById(Long userId);

    MemberVo findByLoginId(String loginId);

    int countAllByLoginId(String loginId);
}
