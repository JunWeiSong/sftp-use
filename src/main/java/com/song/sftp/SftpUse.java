package com.song.sftp;

import com.jcraft.jsch.Session;
import com.song.sftp.utils.Const;
import com.song.sftp.utils.LinuxConnectionHelper;
import com.song.sftp.utils.SftpProgressGuava;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.swing.filechooser.FileSystemView;

/**
 * @Package: com.song.sftp
 * @Description： sftp测试使用,若只有一个服务器连接，可将session提取为公共部分
 * @Author: SongJunWei
 * @Date: 2021/1/20
 * @Modified By:
 */
@RestController
public class SftpUse {

    @ApiOperation(value = "文件目录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "currentCatalog", value = "当前文件目录"),
            @ApiImplicitParam(name = "status", value = "状态：0下级目录，1上级目录")
    })
    @RequestMapping(path = "/catalog/file", method = RequestMethod.GET)
    public Object sshCatalogFile(@RequestParam(name = "currentCatalog", required = false) String currentCatalog,
                                 @RequestParam(name = "status",required = false,defaultValue = "0") Integer status) {
        String backPath = backPath(currentCatalog, status);
        Session session = null;
        try {
            session = LinuxConnectionHelper.connect("192.168.0.1", "root", "123456",
                    22, null, null);
            return LinuxConnectionHelper.listFiles(backPath, session);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            LinuxConnectionHelper.close(session);
        }
        return null;
    }

    @ApiOperation(value = "文件上传",notes ="维护进度，需传入progressKey" )
    @ApiImplicitParam(name = "progressKey", value = "进度key")
    @RequestMapping(path = "/upload/file", method = RequestMethod.GET)
    public void sshUploadFile(MultipartFile file,
                              @RequestParam(name = "progressKey",required = false) String progressKey) {
        Session session = null;
        try {
            String fileName = file.getOriginalFilename();

            session = LinuxConnectionHelper.connect("192.168.0.1", "root", "123456",
                    22, null, null);

            if(StringUtils.isBlank(progressKey)){
                //不维护上传进度
                LinuxConnectionHelper.uploadFile(session, file.getInputStream(), fileName, Const.DEFAULT_PATH);
                return;
            }
            LinuxConnectionHelper.uploadFile(session, file.getInputStream(), fileName, Const.DEFAULT_PATH, file.getSize(),progressKey,true);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            LinuxConnectionHelper.close(session);
        }
    }

    @ApiOperation(value = "文件下载")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "filePath", value = "下载路径", required = true),
            @ApiImplicitParam(name = "uploadPath", value = "下载位置，默认桌面"),
            @ApiImplicitParam(name = "proxyId", value = "proxyId", required = true),
            @ApiImplicitParam(name = "progressKey", value = "进度key"),
    })
    @RequestMapping(path = "/download/file", method = RequestMethod.GET)
    public Object sshDownloadFile(@RequestParam(name = "filePath") String filePath,
                                  @RequestParam(name = "uploadPath", required = false) String uploadPath,
                                  @RequestParam(name = "progressKey",required = false) String progressKey) {

        if (StringUtils.isBlank(filePath)) {
            System.out.println("下载路径不能为空");
        }
        String upPath = StringUtils.isEmpty(uploadPath) ? desktopPath() : uploadPath.trim();
        Session session = null;
        try {
            session = LinuxConnectionHelper.connect("192.168.0.1", "root", "123456",
                    22, null, null);

            if(StringUtils.isBlank(progressKey)){
                //不维护上传进度
                LinuxConnectionHelper.downloadFile(session, filePath.trim(),upPath);
            }
            LinuxConnectionHelper.downloadFile(session, filePath.trim(),upPath,progressKey);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            LinuxConnectionHelper.close(session);
        }
        return null;
    }

    @ApiOperation(value = "上传or下载进度", notes = "定时轮询查看")
    @ApiImplicitParam(name = "progressKey", value = "进度key", required = true)
    @RequestMapping(path = "/upload/percent", method = RequestMethod.GET)
    public Object uploadPercent(@RequestParam(name = "progressKey") String progressKey) {
        return SftpProgressGuava.get(progressKey);
    }

    /**
     * win桌面路径
     *
     * @return path
     */
    public String desktopPath() {
        FileSystemView fsv = FileSystemView.getFileSystemView();
        return fsv.getHomeDirectory().getName();
    }

    /**
     * 返回路径
     *
     * @param currentCatalog 当前路径
     * @param status         0下级目录，1上级目录
     * @return path
     */
    public static String backPath(String currentCatalog, Integer status) {
        if (StringUtils.isBlank(currentCatalog)) {
            return Const.DEFAULT_PATH;
        }
        String path = currentCatalog.trim();
        if ("/".equals(path)) {
            return path;
        }
        //上级目录
        if (Const.STATUS_ENABLE.equals(status)) {
            return path.substring(0, path.lastIndexOf("/"));
        }
        return path;
    }

}
