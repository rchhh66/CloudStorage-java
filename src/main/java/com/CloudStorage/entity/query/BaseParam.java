package com.CloudStorage.entity.query;

import lombok.Data;
//封装文件基本参数的父类
@Data
public class BaseParam {
	private SimplePage simplePage;
	private Integer pageNo;
	private Integer pageSize;
	private String orderBy;

}
