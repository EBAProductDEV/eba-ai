package com.qctv1.ai.iam.client;

import com.qctv1.iam.api.user.IamUserApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "qctv1-iam", path = "/internal")
public interface IamUserFeignClient extends IamUserApi {
}
