package com.qctv1.ai.iam.service;

import com.qctv1.ai.iam.client.IamUserFeignClient;
import com.qctv1.iam.api.common.PageResult;
import com.qctv1.iam.api.user.dto.UserListItemDto;
import com.qctv1.iam.api.user.dto.UserPageQuery;
import com.qctv1.iam.api.user.dto.UserProfileDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class IamUserFeignService {

    private final IamUserFeignClient iamUserFeignClient;

    public IamUserFeignService(IamUserFeignClient iamUserFeignClient) {
        this.iamUserFeignClient = iamUserFeignClient;
    }

    public Mono<UserProfileDto> getCurrentProfile(String userId, String userName, String roleCode) {
        return Mono.fromCallable(() -> iamUserFeignClient.getCurrentProfile(userId, userName, roleCode))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<UserProfileDto> getUserById(Long id, String userId, String userName, String roleCode) {
        return Mono.fromCallable(() -> iamUserFeignClient.getUserById(id, userId, userName, roleCode))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<PageResult<UserListItemDto>> pageUsers(UserPageQuery query, String userId, String userName, String roleCode) {
        return Mono.fromCallable(() -> iamUserFeignClient.pageUsers(query, userId, userName, roleCode))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
