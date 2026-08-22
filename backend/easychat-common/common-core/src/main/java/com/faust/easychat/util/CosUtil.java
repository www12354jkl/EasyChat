package com.faust.easychat.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.config.CosConfig;
import com.faust.easychat.exception.AppException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CosUtil {
    private final CosConfig cosConfig;

    public String upload(MultipartFile file, String path) {
        COSCredentials credentials = new BasicCOSCredentials(cosConfig.getSecretId(), cosConfig.getSecretKey());
        Region region = new Region(cosConfig.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        COSClient cosClient = new COSClient(credentials, clientConfig);

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest request = new PutObjectRequest(
                    cosConfig.getBucket(),
                    path,
                    file.getInputStream(),
                    metadata
            );
            // 设置对象为公有读，以便通过URL直接访问
            request.setCannedAcl(CannedAccessControlList.PublicRead);
            cosClient.putObject(request);

            return cosConfig.getUrlPrefix() + "/" + path;
        } catch (Exception e) {
            throw AppException.internalError("文件上传失败");
        } finally {
            cosClient.shutdown();
        }


    }
}

