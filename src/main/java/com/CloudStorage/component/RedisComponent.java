package com.CloudStorage.component;

import com.CloudStorage.entity.constants.Constants;
import com.CloudStorage.entity.dto.DownloadFileDto;
import com.CloudStorage.entity.dto.SysSettingsDto;
import com.CloudStorage.entity.dto.UserSpaceDto;
import com.CloudStorage.entity.po.FileInfo;
import com.CloudStorage.entity.po.UserInfo;
import com.CloudStorage.entity.query.FileInfoQuery;
import com.CloudStorage.entity.query.UserInfoQuery;
import com.CloudStorage.mappers.FileInfoMapper;
import com.CloudStorage.mappers.UserInfoMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component("redisComponent")
public class RedisComponent {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private FileInfoMapper<FileInfo, FileInfoQuery> fileInfoMapper;

    /**
     * 获取系统设置
     *
     * @return
     */
    public SysSettingsDto getSysSettingsDto() {
        //1.先获取redis中的系统缓存数据
        SysSettingsDto sysSettingsDto = (SysSettingsDto) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
        //2.判断redis中是否有系统缓存数据，如果没有则则存入
        if (sysSettingsDto == null) {
            sysSettingsDto = new SysSettingsDto();
            redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingsDto);
        }
        return sysSettingsDto;
    }
    /**
     * 修改设置
     *
     * @param sysSettingsDto
     */
    public void saveSysSettingsDto(SysSettingsDto sysSettingsDto) {
        //修改redis中的相关数据
        redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingsDto);
    }

    /**
     * 保存下载的密钥
     * @param code
     * @param downloadFileDto
     */
    public void saveDownloadCode(String code, DownloadFileDto downloadFileDto) {
        redisUtils.setex(Constants.REDIS_KEY_DOWNLOAD + code, downloadFileDto,
                Constants.REDIS_KEY_EXPIRES_FIVE_MIN);
    }

    /**
     * 获取下载的密钥
     * @param code
     * @return
     */
    public DownloadFileDto getDownloadCode(String code) {
        return (DownloadFileDto) redisUtils.get(Constants.REDIS_KEY_DOWNLOAD + code);
    }


    /**
     * 获取用户使用的空间
     *
     * @param userId
     * @return
     */
    public UserSpaceDto getUserSpaceUse(String userId) {
        UserSpaceDto spaceDto = (UserSpaceDto) redisUtils.get(Constants.REDIS_KEY_USER_SPACE_USE + userId);
        //如果redis中没有用户空间的相关信息，则到数据库中查用户空间的信息，然后缓存到redis中
        if (null == spaceDto) {
            spaceDto = new UserSpaceDto();
            Long useSpace = this.fileInfoMapper.selectUseSpace(userId);
            spaceDto.setUseSpace(useSpace);
            spaceDto.setTotalSpace(getSysSettingsDto().getUserInitUseSpace() * Constants.MB);
            redisUtils.setex(Constants.REDIS_KEY_USER_SPACE_USE + userId, spaceDto,
                    Constants.REDIS_KEY_EXPIRES_DAY);
        }
        return spaceDto;
    }

    /**
     * 保存已使用的空间
     *
     * @param userId
     */
    public void saveUserSpaceUse(String userId, UserSpaceDto userSpaceDto) {
        redisUtils.setex(Constants.REDIS_KEY_USER_SPACE_USE + userId, userSpaceDto,
                Constants.REDIS_KEY_EXPIRES_DAY);
    }

    /**
     * 重置用户存储空间
     * @param userId
     * @return
     */
    public UserSpaceDto resetUserSpaceUse(String userId) {
        UserSpaceDto spaceDto = new UserSpaceDto();
        Long useSpace = this.fileInfoMapper.selectUseSpace(userId);
        spaceDto.setUseSpace(useSpace);

        UserInfo userInfo = this.userInfoMapper.selectByUserId(userId);
        spaceDto.setTotalSpace(userInfo.getTotalSpace());
        redisUtils.setex(Constants.REDIS_KEY_USER_SPACE_USE + userId, spaceDto,
                Constants.REDIS_KEY_EXPIRES_DAY);
        return spaceDto;
    }

    /**
     * 保存文件临时大小
     * @param userId
     * @param fileId
     * @param fileSize
     */
    public void saveFileTempSize(String userId, String fileId, Long fileSize) {
        Long currentSize = getFileTempSize(userId, fileId);
        redisUtils.setex(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId,
                currentSize + fileSize, Constants.REDIS_KEY_EXPIRES_ONE_HOUR);
    }

    /**
     * 获取临时文件大小
     * @param userId
     * @param fileId
     * @return
     */
    public Long getFileTempSize(String userId, String fileId) {
        String key=Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId;
        Object sizeObj = redisUtils.get(key);
        if (sizeObj == null) {
            return 0L;
        }
        if (sizeObj instanceof Integer) {
            return ((Integer) sizeObj).longValue();
        } else if (sizeObj instanceof Long) {
            return (Long) sizeObj;
        }
        return 0L;
        //Long currentSize = getFileSizeFromRedis(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId);
        //return currentSize;
    }

    /**
     *
     * @param key
     * @return
     */
    /*private Long getFileSizeFromRedis(String key) {
        Object sizeObj = redisUtils.get(key);
        if (sizeObj == null) {
            return 0L;
        }
        if (sizeObj instanceof Integer) {
            return ((Integer) sizeObj).longValue();
        } else if (sizeObj instanceof Long) {
            return (Long) sizeObj;
        }

        return 0L;
    }*/
}
