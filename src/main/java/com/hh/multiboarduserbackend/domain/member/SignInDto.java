package com.hh.multiboarduserbackend.domain.member;

import lombok.Builder;

@Builder
public record SignInDto(
          String loginId
        , String password
) {
}
