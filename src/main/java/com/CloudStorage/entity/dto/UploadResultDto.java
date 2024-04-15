package com.CloudStorage.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
//上传文件的响应类，记录文件的状态
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadResultDto implements Serializable {
    private String fileId;
    private String status;
}
