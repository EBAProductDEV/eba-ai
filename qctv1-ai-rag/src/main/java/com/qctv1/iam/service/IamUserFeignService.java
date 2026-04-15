package com.qctv1.iam.service;

import com.qctv1.iam.api.common.PageResult;
import com.qctv1.iam.api.user.dto.UserListItemDto;
import com.qctv1.iam.api.user.dto.UserPageQuery;
import com.qctv1.iam.api.user.dto.UserProfileDto;
import com.qctv1.iam.client.IamUserFeignClient;
import org.springframework.stereotype.Service;

@Service
public class IamUserFeignService {

    private final IamUserFeignClient iamUserFeignClient;

    public IamUserFeignService(IamUserFeignClient iamUserFeignClient) {
        this.iamUserFeignClient = iamUserFeignClient;
    }

    public UserProfileDto getCurrentProfile(String userId, String userName, String roleCode) {
        return iamUserFeignClient.getCurrentProfile(userId, userName, roleCode);
    }

    public UserProfileDto getUserById(Long id, String userId, String userName, String roleCode) {
        return iamUserFeignClient.getUserById(id, userId, userName, roleCode);
    }

    public PageResult<UserListItemDto> pageUsers(UserPageQuery query, String userId, String userName, String roleCode) {
        return iamUserFeignClient.pageUsers(query, userId, userName, roleCode);
    }
}
