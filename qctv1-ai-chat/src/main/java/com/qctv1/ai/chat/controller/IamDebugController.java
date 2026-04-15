package com.qctv1.ai.chat.controller;

import com.qctv1.ai.iam.service.IamUserFeignService;
import com.qctv1.iam.api.common.PageResult;
import com.qctv1.iam.api.header.IamUserHeaders;
import com.qctv1.iam.api.user.dto.UserListItemDto;
import com.qctv1.iam.api.user.dto.UserPageQuery;
import com.qctv1.iam.api.user.dto.UserProfileDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/chat/debug")
public class IamDebugController {

    private final IamUserFeignService iamUserFeignService;

    public IamDebugController(IamUserFeignService iamUserFeignService) {
        this.iamUserFeignService = iamUserFeignService;
    }

    @GetMapping("/current-user")
    public Mono<UserProfileDto> currentUser(
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = IamUserHeaders.USER_NAME, required = false) String userName,
            @RequestHeader(value = IamUserHeaders.USER_ROLE, required = false) String roleCode
    ) {
        return iamUserFeignService.getCurrentProfile(userId, userName, roleCode);
    }

    @GetMapping("/users/{id}")
    public Mono<UserProfileDto> getUserById(
            @PathVariable("id") Long id,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = IamUserHeaders.USER_NAME, required = false) String userName,
            @RequestHeader(value = IamUserHeaders.USER_ROLE, required = false) String roleCode
    ) {
        return iamUserFeignService.getUserById(id, userId, userName, roleCode);
    }

    @PostMapping("/users/page")
    public Mono<PageResult<UserListItemDto>> pageUsers(
            @Valid @RequestBody UserPageQuery query,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = IamUserHeaders.USER_NAME, required = false) String userName,
            @RequestHeader(value = IamUserHeaders.USER_ROLE, required = false) String roleCode
    ) {
        return iamUserFeignService.pageUsers(query, userId, userName, roleCode);
    }
}
