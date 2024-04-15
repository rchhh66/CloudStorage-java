package com.CloudStorage.service.impl;

import com.CloudStorage.component.RedisComponent;
import com.CloudStorage.entity.config.AppConfig;
import com.CloudStorage.entity.constants.Constants;
import com.CloudStorage.entity.dto.SessionWebUserDto;
import com.CloudStorage.entity.dto.UploadResultDto;
import com.CloudStorage.entity.dto.UserSpaceDto;
import com.CloudStorage.entity.enums.*;
import com.CloudStorage.entity.po.FileInfo;
import com.CloudStorage.entity.po.UserInfo;
import com.CloudStorage.entity.query.FileInfoQuery;
import com.CloudStorage.entity.query.SimplePage;
import com.CloudStorage.entity.query.UserInfoQuery;
import com.CloudStorage.entity.vo.PaginationResultVO;
import com.CloudStorage.exception.BusinessException;
import com.CloudStorage.mappers.FileInfoMapper;
import com.CloudStorage.mappers.UserInfoMapper;
import com.CloudStorage.service.FileInfoService;
import com.CloudStorage.utils.DateUtil;
import com.CloudStorage.utils.ProcessUtils;
import com.CloudStorage.utils.ScaleFilter;
import com.CloudStorage.utils.StringTools;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 文件信息 业务接口实现
 */
@Service("fileInfoService")
public class FileInfoServiceImpl implements FileInfoService {

    private static final Logger logger = LoggerFactory.getLogger(FileInfoServiceImpl.class);

    @Resource
    @Lazy//防止循环依赖
    private FileInfoServiceImpl fileInfoService;

    @Resource
    private AppConfig appConfig;


    @Resource
    private FileInfoMapper<FileInfo, FileInfoQuery> fileInfoMapper;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private RedisComponent redisComponent;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<FileInfo> findListByParam(FileInfoQuery param) {
        return this.fileInfoMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(FileInfoQuery param) {
        return this.fileInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<FileInfo> findListByPage(FileInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileInfo> list = this.findListByParam(param);
        PaginationResultVO<FileInfo> result = new PaginationResultVO(count, page.getPageSize(),
                page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(FileInfo bean) {
        return this.fileInfoMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileInfoMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileInfoMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 根据FileIdAndUserId获取对象
     */
    @Override
    public FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
    }

    /**
     * 根据FileIdAndUserId修改
     */
    @Override
    public Integer updateFileInfoByFileIdAndUserId(FileInfo bean, String fileId, String userId) {
        return this.fileInfoMapper.updateByFileIdAndUserId(bean, fileId, userId);
    }

    /**
     * 根据FileIdAndUserId删除
     */
    @Override
    public Integer deleteFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.fileInfoMapper.deleteByFileIdAndUserId(fileId, userId);
    }

    /**
     * 文件上传
     * @param webUserDto
     * @param fileId
     * @param file
     * @param fileName
     * @param filePid
     * @param fileMd5
     * @param chunkIndex
     * @param chunks
     * @return
     */
    /**
     文件分片上传通常用于大文件上传，可以将一个大文件划分为多个小块，每个小块分别上传至服务器，
     并在服务器端进行合并。这样可以大大提高文件上传的效率和可靠性。
     1.客户端将文件按照指定大小分成多个块，每个块都带上自己的索引号、总片数和当前块的字节数组。然后通过 HTTP 请求将这些
     块一次性传给服务端。
     2.服务端接收到请求后，将每个块存储到临时目录中，以索引号作为文件名，将所有块存储完毕后返回一个确认信息。
     3.客户端收到确认信息后，再发送一个请求告诉服务端所有块已经上传完毕。
     4.服务端检查所有块是否已经上传完成，并进行合并。如果有任何一个块没有上传成功，则返回失败信息。如果合并成功，则返回成功
     信息。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto uploadFile(SessionWebUserDto webUserDto, String fileId, MultipartFile file, String fileName, String filePid, String fileMd5,
                                      Integer chunkIndex, Integer chunks) {
        File tempFileFolder = null;
        //用于判断是否有异常
        Boolean uploadSuccess = true;
        UploadResultDto resultDto = new UploadResultDto();
        try {
            //1.先判断是否有fileId,没有就调用工具类生成
            if (StringTools.isEmpty(fileId)) {
                fileId = StringTools.getRandomString(Constants.LENGTH_10);
            }
            //1.1将生成的fileId放入封装好了的结果类中
            resultDto.setFileId(fileId);
            Date curDate = new Date();
            //2.获取redis中缓存的用户已使用的空间，用于先行判断剩余的空间是否还能支持文件的上传
            UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());
            //3.对文件的分片索引进行相关的判断操作
            //3.1先取第一个分片获取MD5值到数据库进行查询，判断是否存在，如果存在则可以进行秒传
            /**
             * 实现文件秒传的主要思路是通过比较文件的 MD5 值来判断文件是否已经存在于服务器上。当用户上传一个文件时，
             *我们可以先计算这个文件的 MD5 值，然后将其和服务器上所有已有文件的 MD5 值进行比较。如果发现有相同的 MD5值，
             * 就说明这个文件已经存在于服务器上了，可以直接返回给用户上传成功的消息。
             */
            if (chunkIndex == 0) {
                //3.1.1创建一个封装了文件信息的类
                FileInfoQuery infoQuery = new FileInfoQuery();
                //3.1.2将相关信息封装到该类中
                infoQuery.setFileMd5(fileMd5);
                infoQuery.setSimplePage(new SimplePage(0, 1));
                infoQuery.setStatus(FileStatusEnums.USING.getStatus());
                //3.1.3对该文件进行查询
                List<FileInfo> dbFileList = this.fileInfoMapper.selectList(infoQuery);
                //3.1.4如果数据库存在相同md5值的文件则可进行秒传
                if (!dbFileList.isEmpty()) {
                    FileInfo dbFile = dbFileList.get(0);//此时这个文件为总文件
                    //3.1.5判断文件状态
                    if (dbFile.getFileSize() + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
                        //判断是否还有空间存文件
                        throw new BusinessException(ResponseCodeEnum.CODE_904);
                    }
                    //如果还有空间，直接将文件信息类复制插入数据库
                    dbFile.setFileId(fileId);
                    dbFile.setFilePid(filePid);
                    dbFile.setUserId(webUserDto.getUserId());
                    dbFile.setFileMd5(null);
                    dbFile.setCreateTime(curDate);
                    dbFile.setLastUpdateTime(curDate);
                    dbFile.setStatus(FileStatusEnums.USING.getStatus());
                    dbFile.setDelFlag(FileDelFlagEnums.USING.getFlag());
                    dbFile.setFileMd5(fileMd5);
                    //判断文件是否需要重命名
                    fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
                    dbFile.setFileName(fileName);
                    this.fileInfoMapper.insert(dbFile);
                    resultDto.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
                    //更新用户空间使用
                    updateUserSpace(webUserDto, dbFile.getFileSize());
                    return resultDto;
                }
            }
            //4.如果不能进行秒传，则进行分片上传
            //4.1判断磁盘空间
            //4.1.1在redis中获取前几次分片上传文件内存大小
            Long currentTempSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
            //4.1.2判断空间是否充足，现在分片上传文件的大小加上之前分片上传文件的大小再加上已使用的空间来判断内存是否充足
            if (file.getSize() + currentTempSize + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.CODE_904);
            }
            //4.2如果空间充足则暂存在临时目录
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = webUserDto.getUserId() + fileId;
            tempFileFolder = new File(tempFolderName + currentUserFolderName);
            //4.2.1判断临时目录是否存在，不存在则创建
            if (!tempFileFolder.exists()) {
                tempFileFolder.mkdirs();
            }
            //4.3进行文件上传
            File newFile = new File(tempFileFolder.getPath() + "/" + chunkIndex);
            file.transferTo(newFile);
            //4.4保存临时文件大小到redis
            redisComponent.saveFileTempSize(webUserDto.getUserId(), fileId, file.getSize());
            //4.5不是最后一个分片，直接返回
            if (chunkIndex < chunks - 1) {
                resultDto.setStatus(UploadStatusEnums.UPLOADING.getCode());
                return resultDto;
            }
            redisComponent.saveFileTempSize(webUserDto.getUserId(),fileId,file.getSize());
            //5.最后一个分片上传完成，记录数据库，异步合并分片
            String month = DateUtil.format(curDate, DateTimePatternEnum.YYYYMM.getPattern());
            String fileSuffix = StringTools.getFileSuffix(fileName);
            //5.1真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            FileTypeEnums fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
            //5.2自动重命名
            fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
            //5.3将文件信息存储到数据库
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileId(fileId);
            fileInfo.setUserId(webUserDto.getUserId());
            fileInfo.setFileMd5(fileMd5);
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(month+"/"+realFileName);
            fileInfo.setFilePid(filePid);
            fileInfo.setCreateTime(curDate);
            fileInfo.setLastUpdateTime(curDate);
            fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
            fileInfo.setFileType(fileTypeEnum.getType());
            fileInfo.setStatus(FileStatusEnums.TRANSFER.getStatus());
            fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            this.fileInfoMapper.insert(fileInfo);
            //5.4更新redis中的使用空间
            Long totalSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
            updateUserSpace(webUserDto, totalSize);
            resultDto.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());
            //6.事务提交后调用异步方法合并文件转码
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileInfoService.transferFile(fileInfo.getFileId(), webUserDto);
                }
            });
            return resultDto;
        } catch (BusinessException e) {
            uploadSuccess = false;
            logger.error("文件上传失败", e);
            throw e;
        } catch (Exception e) {
            uploadSuccess = false;
            logger.error("文件上传失败", e);
            throw new BusinessException("文件上传失败");
        } finally {
            //7.如果上传失败，清除临时目录
            if (tempFileFolder != null && !uploadSuccess) {
                try {
                    FileUtils.deleteDirectory(tempFileFolder);
                } catch (IOException e) {
                    logger.error("删除临时目录失败");
                }
            }
        }
    }

    /**
     * 更新用户使用空间
     * @param webUserDto
     * @param useSpace
     */
    private void updateUserSpace(SessionWebUserDto webUserDto, Long useSpace) {
        Integer count = userInfoMapper.updateUserSpace(webUserDto.getUserId(),useSpace, null);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }
        //将用户存储空间的基本信息上传到redis中
        UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());
        spaceDto.setUseSpace(spaceDto.getUseSpace() + useSpace);
        redisComponent.saveUserSpaceUse(webUserDto.getUserId(), spaceDto);
    }

    /**
     * 判断文件是否需要重命名
     * @param filePid
     * @param userId
     * @param fileName
     * @return
     */
    private String autoRename(String filePid, String userId, String fileName) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoQuery.setFileName(fileName);
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            return StringTools.rename(fileName);
        }
        return fileName;
    }

    /**
     * 将文件合并转码存到真实目录下
     * @param fileId
     * @param webUserDto
     */
    @Async
    public void transferFile(String fileId, SessionWebUserDto webUserDto) {
        Boolean transferSuccess = true;//用于判断合并转码是否成功
        String targetFilePath = null;
        String cover = null;
        FileTypeEnums fileTypeEnum = null;
        FileInfo fileInfo = fileInfoMapper.selectByFileIdAndUserId(fileId, webUserDto.getUserId());
        try {
            if (fileInfo == null || !FileStatusEnums.TRANSFER.getStatus().equals(fileInfo.getStatus())) {
                return;
            }
            //1.找到临时目录再进行一次判断兜底
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = webUserDto.getUserId() + fileId;
            File fileFolder = new File(tempFolderName + currentUserFolderName);
            if (!fileFolder.exists()) {
                fileFolder.mkdirs();
            }
            //2.文件后缀
            String fileSuffix = StringTools.getFileSuffix(fileInfo.getFileName());
            String month = DateUtil.format(fileInfo.getCreateTime(), DateTimePatternEnum.YYYYMM.getPattern());
            //3.目标目录
            String targetFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFolder = new File(targetFolderName + "/" + month);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }
            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            //真实文件路径
            targetFilePath = targetFolder.getPath() + "/" + realFileName;
            //4.合并文件
            union(fileFolder.getPath(), targetFilePath, fileInfo.getFileName(), true);
            //5.视频文件切割
            //5.1先判断文件类型
            fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
            if (FileTypeEnums.VIDEO == fileTypeEnum) {
                //5.2视频生成缩略图
                cutFile4Video(fileId, targetFilePath);
                cover = month + "/" + currentUserFolderName + Constants.IMAGE_PNG_SUFFIX;
                String coverPath = targetFolderName + "/" + cover;
                //5.2.1通过ffmpeg生成视频封面图
                ScaleFilter.createCover4Video(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath));
            } else if (FileTypeEnums.IMAGE == fileTypeEnum) {
                //5.3将图片生成缩略图
                cover = month + "/" + realFileName.replace(".", "_.");
                String coverPath = targetFolderName + "/" + cover;
                Boolean created = ScaleFilter.createThumbnailWidthFFmpeg(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath), false);
                if (!created) {
                    FileUtils.copyFile(new File(targetFilePath), new File(coverPath));
                }
            }
        } catch (Exception e) {
            logger.error("文件转码失败，文件Id:{},userId:{}", fileId, webUserDto.getUserId(), e);
            transferSuccess = false;
        } finally {
            //更新文件相关信息
            FileInfo updateInfo = new FileInfo();
            updateInfo.setFileSize(new File(targetFilePath).length());
            updateInfo.setFileCover(cover);
            //文件状态由转码中转化为成功或失败
            updateInfo.setStatus(transferSuccess ? FileStatusEnums.USING.getStatus() : FileStatusEnums.TRANSFER_FAIL.getStatus());
            fileInfoMapper.updateFileStatusWithOldStatus(fileId, webUserDto.getUserId(), updateInfo, FileStatusEnums.TRANSFER.getStatus());
        }
    }

    /**
     * 合并文件
     * @param dirPath
     * @param toFilePath
     * @param fileName
     * @param delSource
     * @throws BusinessException
     */
    public static void union(String dirPath, String toFilePath, String fileName, boolean delSource) throws BusinessException {
        File dir = new File(dirPath);
        //1.判断目录是否存在
        if (!dir.exists()) {
            throw new BusinessException("目录不存在");
        }
        //2.读取文件夹中的文件数组
        File fileList[] = dir.listFiles();
        File targetFile = new File(toFilePath);
        /**
         * RandomAccessFile 是 Java 中的一个用于支持随机访问文件的类，它不仅可以像传统的 IO 流一样进行顺序读写操作，
         * 还可以随意移动文件指针执行任意位置的读写操作。
         */
        RandomAccessFile writeFile = null;
        try {
            /**
             * 这行代码创建了一个RandomAccessFile对象，并将其赋值给writeFile变量。RandomAccessFile类是Java中用于读写文件的类，
             * 可以在文件中随机访问和修改数据。
             * 构造函数参数targetFile表示要操作的文件的路径和文件名。"rw"表示以读写方式打开文件，即可以读取文件内容，也可以向文件中写入数据。
             */
            writeFile = new RandomAccessFile(targetFile, "rw");
            //3.创建缓冲流
            byte[] b = new byte[1024 * 10];
            for (int i = 0; i < fileList.length; i++) {
                int len = -1;
                //4.创建读块文件的对象
                File chunkFile = new File(dirPath + File.separator + i);//File.separator文件路径分隔符：\
                RandomAccessFile readFile = null;
                try {
                  //5.合并文件
                    //5.1创建用于读取临时文件的RandomAccessFile对象readFile
                    readFile = new RandomAccessFile(chunkFile, "r");
                    while ((len = readFile.read(b)) != -1) {
                        //5.1将读取的临时文件写入writeFile对象中
                        writeFile.write(b, 0, len);
                    }
                } catch (Exception e) {
                    logger.error("合并分片失败", e);
                    throw new BusinessException("合并文件失败");
                } finally {
                    readFile.close();
                }
            }
        } catch (Exception e) {
            logger.error("合并文件:{}失败", fileName, e);
            throw new BusinessException("合并文件" + fileName + "出错了");
        } finally {
            try {
                if (null != writeFile) {
                    writeFile.close();
                }
            } catch (IOException e) {
                logger.error("关闭流失败", e);
            }
            if (delSource) {
                if (dir.exists()) {
                    try {
                        FileUtils.deleteDirectory(dir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 视频切割
     * @param fileId
     * @param videoFilePath
     */
    private void cutFile4Video(String fileId, String videoFilePath) {
        //1.创建同名切片目录
        File tsFolder = new File(videoFilePath.substring(0, videoFilePath.lastIndexOf(".")));
        if (!tsFolder.exists()) {
            tsFolder.mkdirs();
        }
        //2.列出需要使用的ffmpeg文件
        final String CMD_TRANSFER_2TS = "ffmpeg -y -i %s  -vcodec copy -acodec copy -vbsf h264_mp4toannexb %s";
        final String CMD_CUT_TS = "ffmpeg -i %s -c copy -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%4d.ts";
        String tsPath = tsFolder + "/" + Constants.TS_NAME;
        //3.生成.ts
        String cmd = String.format(CMD_TRANSFER_2TS, videoFilePath, tsPath);
        ProcessUtils.executeCommand(cmd, false);
        //4.生成索引文件.m3u8 和切片.ts
        cmd = String.format(CMD_CUT_TS, tsPath, tsFolder.getPath() + "/" + Constants.M3U8_NAME, tsFolder.getPath(), fileId);
        ProcessUtils.executeCommand(cmd, false);
        //删除index.ts
        new File(tsPath).delete();
    }

    /**
     * 文件重命名
     * @param fileId
     * @param userId
     * @param fileName
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo rename(String fileId, String userId, String fileName) {
        //1.先判断文件夹或文件是否存在
        FileInfo fileInfo = this.fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }
        //2.获取父文件的id
        String filePid = fileInfo.getFilePid();
        //3.判断同级文件是否同名
        checkFileName(filePid, userId, fileName, fileInfo.getFolderType());
        //4.取出文件后缀，更改文件名不能更改后缀
        if (FileFolderTypeEnums.FILE.getType().equals(fileInfo.getFolderType())) {
            fileName = fileName + StringTools.getFileSuffix(fileInfo.getFileName());
        }
        Date curDate = new Date();
        FileInfo dbInfo = new FileInfo();
        dbInfo.setFileName(fileName);
        dbInfo.setLastUpdateTime(curDate);
        //5.更新数据库
        this.fileInfoMapper.updateByFileIdAndUserId(dbInfo, fileId, userId);
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFileName(fileName);
        //5.1过滤已经移入到数据库中的文件
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        //6.再次判断文件是否重名
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 1) {
            throw new BusinessException("文件名" + fileName + "已经存在");
        }
        fileInfo.setFileName(fileName);
        fileInfo.setLastUpdateTime(curDate);
        return fileInfo;
    }

    /**
     * 判断同级目录下文件是否重名
     * @param filePid
     * @param userId
     * @param fileName
     * @param folderType
     */
    private void checkFileName(String filePid, String userId, String fileName, Integer folderType) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFolderType(folderType);
        fileInfoQuery.setFileName(fileName);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        //过滤已经移入到数据库中的文件
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            throw new BusinessException("此目录下已存在同名文件，请修改名称");
        }
    }

    /**
     * 创建目录
     * @param filePid
     * @param userId
     * @param folderName
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo newFolder(String filePid, String userId, String folderName) {
        checkFileName(filePid, userId, folderName, FileFolderTypeEnums.FOLDER.getType());
        Date curDate = new Date();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(StringTools.getRandomString(Constants.LENGTH_10));
        fileInfo.setUserId(userId);
        fileInfo.setFilePid(filePid);
        fileInfo.setFileName(folderName);
        fileInfo.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setStatus(FileStatusEnums.USING.getStatus());
        //过滤已经移入到数据库中的文件
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        this.fileInfoMapper.insert(fileInfo);

        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFileName(folderName);
        fileInfoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 1) {
            throw new BusinessException("文件夹" + folderName + "已经存在");
        }
        fileInfo.setFileName(folderName);
        fileInfo.setLastUpdateTime(curDate);
        return fileInfo;
    }

    /**
     *移动文件
     * @param fileIds
     * @param filePid
     * @param userId
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeFileFolder(String fileIds, String filePid, String userId) {
        if (fileIds.equals(filePid)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (!Constants.ZERO_STR.equals(filePid)) {
            FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(filePid, userId);
            if (fileInfo == null || !FileDelFlagEnums.USING.getFlag().equals(fileInfo.getDelFlag())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }
        String[] fileIdArray = fileIds.split(",");

        FileInfoQuery query = new FileInfoQuery();
        query.setFilePid(filePid);
        query.setUserId(userId);
        List<FileInfo> dbFileList = fileInfoService.findListByParam(query);

        Map<String, FileInfo> dbFileNameMap = dbFileList.stream().collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));
        //查询选中的文件
        query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        List<FileInfo> selectFileList = fileInfoService.findListByParam(query);

        //将所选文件重命名
        for (FileInfo item : selectFileList) {
            FileInfo rootFileInfo = dbFileNameMap.get(item.getFileName());
            //文件名已经存在，重命名被还原的文件名
            FileInfo updateInfo = new FileInfo();
            if (rootFileInfo != null) {
                String fileName = StringTools.rename(item.getFileName());
                updateInfo.setFileName(fileName);
            }
            updateInfo.setFilePid(filePid);
            this.fileInfoMapper.updateByFileIdAndUserId(updateInfo, item.getFileId(), userId);
        }
    }

    /**
     *将文件移到回收站
     * @param userId
     * @param fileIds
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFile2RecycleBatch(String userId, String fileIds) {
        //1.将要删除的文件以逗号为分隔存储到一个数组中
        String[] fileIdArray = fileIds.split(",");
        //2.创建一个文件信息类
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        //3.查询需要删除的文件
        List<FileInfo> fileInfoList = this.fileInfoMapper.selectList(query);
        if (fileInfoList.isEmpty()) {
            return;
        }
        List<String> delFilePidList = new ArrayList<>();
        for (FileInfo fileInfo : fileInfoList) {
            //4.利用递归将所有需要查询的子文件以及子文件夹查出
            findAllSubFolderFileIdList(delFilePidList, userId, fileInfo.getFileId(), FileDelFlagEnums.USING.getFlag());
        }
        //5.将目录下的所有文件更新为已删除
        if (!delFilePidList.isEmpty()) {
            FileInfo updateInfo = new FileInfo();
            updateInfo.setDelFlag(FileDelFlagEnums.DEL.getFlag());
            this.fileInfoMapper.updateFileDelFlagBatch(updateInfo, userId, delFilePidList, null, FileDelFlagEnums.USING.getFlag());
        }
        //6.将选中的文件更新为回收站
        List<String> delFileIdList = Arrays.asList(fileIdArray);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setRecoveryTime(new Date());
        fileInfo.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList, FileDelFlagEnums.USING.getFlag());
    }

    /**
     *文件夹递归遍历
     * @param fileIdList
     * @param userId
     * @param fileId
     * @param delFlag
     */
    private void findAllSubFolderFileIdList(List<String> fileIdList, String userId, String fileId, Integer delFlag) {
        fileIdList.add(fileId);
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFilePid(fileId);
        query.setDelFlag(delFlag);
        query.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        List<FileInfo> fileInfoList = this.fileInfoMapper.selectList(query);
        for (FileInfo fileInfo : fileInfoList) {
            findAllSubFolderFileIdList(fileIdList, userId, fileInfo.getFileId(), delFlag);
        }
    }

    /**
     *恢复文件
     * @param userId
     * @param fileIds
     */
    //逻辑与将文件移到回收站相反
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFileBatch(String userId, String fileIds) {
        //1.将要回收的文件id根据逗号为分隔存入到字符串数组中
        String[] fileIdArray = fileIds.split(",");
        //2.创建文件信息类
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        //3根据文件信息进行查询
        List<FileInfo> fileInfoList = this.fileInfoMapper.selectList(query);
        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        //4.找到所选文件子目录文件ID
        for (FileInfo fileInfo : fileInfoList) {
            if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
                findAllSubFolderFileIdList(delFileSubFolderFileIdList, userId, fileInfo.getFileId(), FileDelFlagEnums.DEL.getFlag());
            }
        }
        //5.查询所有根目录的文件
        query = new FileInfoQuery();
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        query.setFilePid(Constants.ZERO_STR);
        List<FileInfo> allRootFileList = this.fileInfoMapper.selectList(query);

        Map<String, FileInfo> rootFileMap = allRootFileList.stream().collect(Collectors.
                toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));

        //查询所有所选文件
        //6.将目录下的所有删除的文件更新为正常
        if (!delFileSubFolderFileIdList.isEmpty()) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, delFileSubFolderFileIdList,
                    null, FileDelFlagEnums.DEL.getFlag());
        }
        //7.将选中的文件更新为正常,且父级目录到根目录
        List<String> delFileIdList = Arrays.asList(fileIdArray);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfo.setFilePid(Constants.ZERO_STR);
        fileInfo.setLastUpdateTime(new Date());
        this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, null,
                delFileIdList, FileDelFlagEnums.RECYCLE.getFlag());
        //8.将所选文件重命名
        for (FileInfo item : fileInfoList) {
            FileInfo rootFileInfo = rootFileMap.get(item.getFileName());
            //8.1文件名已经存在，重命名被还原的文件名
            if (rootFileInfo != null) {
                String fileName = StringTools.rename(item.getFileName());
                FileInfo updateInfo = new FileInfo();
                updateInfo.setFileName(fileName);
                this.fileInfoMapper.updateByFileIdAndUserId(updateInfo, item.getFileId(), userId);
            }
        }
    }
    /**
     *删除文件
     * @param userId
     * @param fileIds
     * @param adminOp
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFileBatch(String userId, String fileIds, Boolean adminOp) {
        //1.将文件id分隔
        String[] fileIdArray = fileIds.split(",");
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        //2.到数据库查询要删除的文件
        List<FileInfo> fileInfoList = this.fileInfoMapper.selectList(query);
        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        //3.找到所选文件子目录文件ID
        for (FileInfo fileInfo : fileInfoList) {
            if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
                findAllSubFolderFileIdList(delFileSubFolderFileIdList, userId, fileInfo.getFileId(), FileDelFlagEnums.DEL.getFlag());
            }
        }

        //4.删除所选文件的子目录中的文件
        if (!delFileSubFolderFileIdList.isEmpty()) {
            //4.1调用Mapper删除数据库
            this.fileInfoMapper.delFileBatch(userId, delFileSubFolderFileIdList,
                    null, adminOp ? null : FileDelFlagEnums.DEL.getFlag());
        }
        //5.删除所选文件
        this.fileInfoMapper.delFileBatch(userId, null, Arrays.asList(fileIdArray),
                adminOp ? null : FileDelFlagEnums.RECYCLE.getFlag());
        //6.更新数据库中的用户空间使用情况
        Long useSpace = this.fileInfoMapper.selectUseSpace(userId);
        UserInfo userInfo = new UserInfo();
        userInfo.setUseSpace(useSpace);
        this.userInfoMapper.updateByUserId(userInfo, userId);
        //7.更新redis中缓存的用户使用空间
        UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(userId);
        userSpaceDto.setUseSpace(useSpace);
        redisComponent.saveUserSpaceUse(userId, userSpaceDto);

    }

    /**
     *用于确认根目录
     * @param rootFilePid
     * @param userId
     * @param fileId
     */
    @Override
    public void checkRootFilePid(String rootFilePid, String userId, String fileId) {
       //如果fileId为空，直接拦截报异常
        if (StringTools.isEmpty(fileId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //
        if (rootFilePid.equals(fileId)) {
            return;
        }
        //递归调用
        checkFilePid(rootFilePid, fileId, userId);
    }

    /**
     *确定文件父级
     * @param rootFilePid
     * @param fileId
     * @param userId
     */
    private void checkFilePid(String rootFilePid, String fileId, String userId) {
        FileInfo fileInfo = this.fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (Constants.ZERO_STR.equals(fileInfo.getFilePid())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (fileInfo.getFilePid().equals(rootFilePid)) {
            return;
        }
        checkFilePid(rootFilePid, fileInfo.getFilePid(), userId);
    }

    /**
     *增加文件分享
     * @param shareRootFilePid
     * @param shareFileIds
     * @param myFolderId
     * @param shareUserId
     * @param cureentUserId
     */
    @Override
    public void saveShare(String shareRootFilePid, String shareFileIds, String myFolderId, String shareUserId, String cureentUserId) {
        String[] shareFileIdArray = shareFileIds.split(",");
        //目标目录文件列表
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(cureentUserId);
        fileInfoQuery.setFilePid(myFolderId);
        List<FileInfo> currentFileList = this.fileInfoMapper.selectList(fileInfoQuery);
        Map<String, FileInfo> currentFileMap = currentFileList.stream().collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));
        //选择的文件
        fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(shareUserId);
        fileInfoQuery.setFileIdArray(shareFileIdArray);
        List<FileInfo> shareFileList = this.fileInfoMapper.selectList(fileInfoQuery);
        //重命名选择的文件
        List<FileInfo> copyFileList = new ArrayList<>();
        Date curDate = new Date();
        for (FileInfo item : shareFileList) {
            FileInfo haveFile = currentFileMap.get(item.getFileName());
            if (haveFile != null) {
                item.setFileName(StringTools.rename(item.getFileName()));
            }
            findAllSubFile(copyFileList, item, shareUserId, cureentUserId, curDate, myFolderId);
        }
        System.out.println(copyFileList.size());
        this.fileInfoMapper.insertBatch(copyFileList);
    }
    /**
     *
     * @param copyFileList
     * @param fileInfo
     * @param sourceUserId
     * @param currentUserId
     * @param curDate
     * @param newFilePid
     */
    private void findAllSubFile(List<FileInfo> copyFileList, FileInfo fileInfo, String sourceUserId, String currentUserId, Date curDate, String newFilePid) {
        String sourceFileId = fileInfo.getFileId();
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setFilePid(newFilePid);
        fileInfo.setUserId(currentUserId);
        String newFileId = StringTools.getRandomString(Constants.LENGTH_10);
        fileInfo.setFileId(newFileId);
        copyFileList.add(fileInfo);
        if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
            FileInfoQuery query = new FileInfoQuery();
            query.setFilePid(sourceFileId);
            query.setUserId(sourceUserId);
            List<FileInfo> sourceFileList = this.fileInfoMapper.selectList(query);
            for (FileInfo item : sourceFileList) {
                findAllSubFile(copyFileList, item, sourceUserId, currentUserId, curDate, newFileId);
            }
        }
    }

    /**
     *
     * @param userId
     * @return
     */
    @Override
    public Long getUserUseSpace(String userId) {
        return this.fileInfoMapper.selectUseSpace(userId);
    }

    /**
     * 根据文件id删除文件
     * @param userId
     */
    @Override
    public void deleteFileByUserId(String userId) {
        this.fileInfoMapper.deleteFileByUserId(userId);
    }
}