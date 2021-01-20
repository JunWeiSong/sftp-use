package com.song.sftp.utils;

import com.jcraft.jsch.*;
import com.song.sftp.vo.ProgressVo;
import com.song.sftp.vo.SftpVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * linux操作工具
 */
public class LinuxConnectionHelper {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LinuxConnectionHelper.class);
    /**
     * 设置超时为5分钟
     */
    private static final int TIME_OUT = 5 * 60 * 1000;
    //session缓存
    private static final Map<String, Session> cache = new HashMap<>();

    public static boolean RUN = false;

    public static final String SH_END = "###END$$$";
    public static final String SH_BEGIN = "###BEGIN$$$";

    public static ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
            new BasicThreadFactory.Builder().namingPattern("clear-session-pool-%d").daemon(true).build());


    /**
     * 创建支持秘钥 连接，需手动关闭
     *
     * @param host        ip地址
     * @param userName    用户名
     * @param password    密码
     * @param port        端口号
     * @param keyFilePath 秘钥路径
     * @param passphrase  秘钥密码
     */
    public static Session connect(String host, String userName, String password, int port, String keyFilePath, String passphrase) throws JSchException {
        //创建对象
        JSch jsch = new JSch();

        if (keyFilePath != null) {
            if (passphrase != null) {
                // 设置私钥
                jsch.addIdentity(keyFilePath, passphrase);
            } else {
                // 设置私钥
                jsch.addIdentity(keyFilePath);
            }
            log.info("连接sftp，私钥文件路径：" + keyFilePath);
        }

        //创建会话
        Session session = jsch.getSession(userName, host, port);
        if (StringUtils.isNotBlank(password)) {
            //输入密码
            session.setPassword(password);
        }
        //配置信息
        Properties config = new Properties();
        //设置不用检查hostKey
        config.setProperty("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
        session.setConfig(config);
        //过期时间
        session.setTimeout(TIME_OUT);
        //建立连接
        session.connect();
        return session;
    }

    /**
     * 创建 连接，需手动关闭
     *
     * @param host     ip地址
     * @param userName 用户名
     * @param password 密码
     * @param port     端口号
     */
    public static Session connect(String host, String userName, String password, int port) throws JSchException {
        //创建对象
        JSch jsch = new JSch();
        //创建会话
        Session session = jsch.getSession(userName, host, port);
        //输入密码
        session.setPassword(password);
        //配置信息
        Properties config = new Properties();
        //设置不用检查hostKey
        config.setProperty("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
        session.setConfig(config);
        //过期时间
        session.setTimeout(TIME_OUT);
        //建立连接
        session.connect();
        return session;
    }

    /**
     * 创建  连接，无需手动关闭
     *
     * @param host     IP地址
     * @param userName 用户名
     * @param password 密码
     * @param port     端口号
     */
    public static Session longConnect(String host, String userName, String password, int port) throws JSchException {
        String key = host + userName + password + port;
        Session session = cache.get(key);
        if (session == null) {
            //创建对象
            JSch jsch = new JSch();
            //创建会话
            session = jsch.getSession(userName, host, port);
            //输入密码
            session.setPassword(password);
            //配置信息
            Properties config = new Properties();
            //设置不用检查hostKey
            config.setProperty("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
            session.setConfig(config);
            session.connect();
            cache.put(key, session);
        } else {
            //判断session是否失效
            if (testSessionIsDown(key)) {
                //session 失去连接则清除
                closeLongSessionByKey(key);
                //重新生成session
                session = longConnect(host, userName, password, port);
            }
        }
        //创建定时器
        createSessionMonitor();
        return session;
    }

    /**
     * 销毁 session
     *
     * @param session 连接session
     */
    public static void close(Session session) {
        if (session != null) {
            session.disconnect();
        }
    }

    /**
     * 测试session是否失效
     *
     * @param key
     * @return true or false
     */
    public static boolean testSessionIsDown(String key) {
        Session session = cache.get(key);
        log.info("测试session是否失效：{}", session);
        if (session == null) {
            return true;
        }
        ChannelExec channelExec = null;
        try {
            log.info("2");
            channelExec = openChannelExec(session);
            channelExec.setCommand("true");
            channelExec.connect();
            log.info("3");
            return false;
        } catch (Throwable e) {
            //session is down
            log.info("4");
            return true;
        } finally {
            if (channelExec != null) {
                channelExec.disconnect();
            }
        }
    }

    /**
     * 销毁 session
     *
     * @param key
     */
    public static synchronized void closeLongSessionByKey(String key) {
        Session session = cache.get(key);
        log.info("销毁 session:{}", session);
        if (session != null) {
            session.disconnect();
            cache.remove(key);
            log.info("销毁 key:{}", key);
        }
    }

    /**
     * 销毁 session
     *
     * @param session
     */
    public static void closeLongSessionBySession(Session session) {
        Iterator iterator = cache.keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            Session oldSession = cache.get(key);
            if (session == oldSession) {
                session.disconnect();
                cache.remove(key);
                return;
            }
        }
    }

    /**
     * 创建一个 sftp 通道并建立连接
     *
     * @param session
     * @return
     */
    public static ChannelSftp openChannelSftp(Session session) throws Exception {
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();
        return channelSftp;
    }

    /**
     * 关闭 sftp 通道
     *
     * @param channelSftp
     */
    public static void closeChannelSftp(ChannelSftp channelSftp) {
        if (channelSftp != null) {
            channelSftp.disconnect();
        }
    }

    /**
     * 下载文件
     *
     * @param remoteFile  远程服务器的文件路径
     * @param localPath   需要保存文件的本地路径
     * @param progressKey 进度维护key
     */
    public static void downloadFile(Session session, String remoteFile, String localPath, String progressKey) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            String remoteFilePath = remoteFile.substring(0, remoteFile.lastIndexOf("/"));
            String remoteFileName = remoteFile.substring(remoteFile.lastIndexOf("/") + 1);
            if (localPath.charAt(localPath.length() - 1) != '/') {
                localPath += '/';
            }
            File file = new File(localPath + remoteFileName);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            OutputStream output = new FileOutputStream(file);
            try {
                channelSftp.cd(remoteFilePath);
                log.info("远程服务器路径：" + remoteFilePath);
                log.info("本地下载路径：" + localPath + remoteFileName);
                SftpATTRS attrs = channelSftp.lstat(remoteFile);

                //初始化下载进度
                SftpProgressGuava.put(progressKey, new ProgressVo("0",
                        LinuxConnectionHelper.readFileSize(attrs.getSize()), 0L, progressKey));

                channelSftp.get(remoteFile, output, new MyProgressMonitor(attrs.getSize(), progressKey));
            } catch (Exception e) {
                throw e;
            } finally {
                output.flush();
                output.close();
            }
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 下载文件
     *
     * @param remoteFile  远程服务器的文件路径
     * @param localPath   需要保存文件的本地路径
     */
    public static void downloadFile(Session session, String remoteFile, String localPath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            String remoteFilePath = remoteFile.substring(0, remoteFile.lastIndexOf("/"));
            String remoteFileName = remoteFile.substring(remoteFile.lastIndexOf("/") + 1);
            if (localPath.charAt(localPath.length() - 1) != '/') {
                localPath += '/';
            }
            File file = new File(localPath + remoteFileName);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            OutputStream output = new FileOutputStream(file);
            try {
                channelSftp.cd(remoteFilePath);
                log.info("远程服务器路径：" + remoteFilePath);
                log.info("本地下载路径：" + localPath + remoteFileName);
                SftpATTRS attrs = channelSftp.lstat(remoteFile);

                channelSftp.get(remoteFile, output, new MyProgressMonitor(-1L, null));
            } catch (Exception e) {
                throw e;
            } finally {
                output.flush();
                output.close();
            }
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }


    /**
     * 上传文件
     *
     * @param session    连接信息
     * @param localFile  本地文件路径
     * @param remotePath 远程服务器路径
     */
    public static void uploadFile(Session session, String localFile, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        if (localFile.contains("\\")) {
            localFile = localFile.replace("\\", "/");
        }
        String remoteFileName = localFile.substring(localFile.lastIndexOf("/") + 1);
        File file = new File(localFile);
        final InputStream input = new FileInputStream(file);
        try {
            channelSftp.cd(remotePath);
        } catch (SftpException e) {
            String tempPath = null;
            try {
                tempPath = remotePath.substring(0, remotePath.lastIndexOf("/"));
                channelSftp.cd(tempPath);
            } catch (SftpException e1) {
                channelSftp.mkdir(tempPath);
            }
            channelSftp.mkdir(remotePath);
            channelSftp.cd(remotePath);
        }
        log.info("远程服务器路径：" + remotePath);
        log.info("本地上传路径：" + localFile);
        try {
            channelSftp.put(input, remoteFileName, new MyProgressMonitor(-1L, null));
        } catch (Exception e) {
            throw e;
        } finally {
            input.close();
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 文件上传,流
     *
     * @param session     session
     * @param inputStream 输入流
     * @param fileName    文件名称
     * @param remotePath  远程路径
     */
    public static void uploadFile(Session session, InputStream inputStream, String fileName, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            channelSftp.cd(remotePath);
        } catch (SftpException e) {
            String tempPath = null;
            try {
                tempPath = remotePath.substring(0, remotePath.lastIndexOf("/"));
                channelSftp.cd(tempPath);
            } catch (SftpException e1) {
                channelSftp.mkdir(tempPath);
            }
            channelSftp.mkdir(remotePath);
            channelSftp.cd(remotePath);
        }
        log.info("远程服务器路径：" + remotePath);
        try {
            channelSftp.put(inputStream, fileName, new MyProgressMonitor(-1L, null));
        } catch (Exception e) {
            throw e;
        } finally {
            inputStream.close();
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 文件上传,流
     * 维护上传进度，可通过SftpProgressGuava查询进度
     *
     * @param session     session
     * @param inputStream 输入流
     * @param fileName    文件名称
     * @param remotePath  上传路径
     * @param fileSize    文件大小
     * @param progressKey 进度key
     * @param flag        是否删除原文件
     */
    public static void uploadFile(Session session, InputStream inputStream, String fileName, String remotePath,
                                  Long fileSize, String progressKey, Boolean flag) throws Exception {
        if(flag){
            //如果文件存在，先删除
            if (LinuxConnectionHelper.fileExist(session, remotePath, fileName)) {
                LinuxConnectionHelper.removeFile(session, remotePath, fileName);
            }
        }
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            channelSftp.cd(remotePath);
        } catch (SftpException e) {
            String tempPath = null;
            try {
                tempPath = remotePath.substring(0, remotePath.lastIndexOf("/"));
                channelSftp.cd(tempPath);
            } catch (SftpException e1) {
                channelSftp.mkdir(tempPath);
            }
            channelSftp.mkdir(remotePath);
            channelSftp.cd(remotePath);
        }
        log.info("远程服务器路径：" + remotePath);
        try {
            //初始化下载进度
            SftpProgressGuava.put(progressKey, new ProgressVo("0",
                    LinuxConnectionHelper.readFileSize(fileSize), 0L, progressKey));

            channelSftp.put(inputStream, fileName, new MyProgressMonitor(fileSize, progressKey), ChannelSftp.OVERWRITE);
        } catch (Exception e) {
            throw e;
        } finally {
            inputStream.close();
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 获取远程服务器文件列表
     *
     * @param session    session
     * @param remotePath 远程服务器路径
     * @return 文件目录
     */
    public static List<String> listFiles(Session session, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            List<String> fileNameList = new ArrayList<>();
            //noinspection rawtypes
            Vector vector = channelSftp.ls(remotePath);
            for (Object o : vector) {
                String fileName = ((ChannelSftp.LsEntry) o).getFilename();
                if (".".equals(fileName) || "..".equals(fileName)) {
                    continue;
                }
                fileNameList.add(fileName);
            }
            return fileNameList;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeChannelSftp(channelSftp);
        }
        return null;
    }

    /**
     * 获取远程服务器文件列表
     * 注：区分文件和目录
     *
     * @param session    session
     * @param remotePath 远程服务器路径
     * @return 文件名称，长度，权限，是否是文件夹
     */
    public static List<SftpVo> listFiles(String remotePath, Session session) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            List<SftpVo> fileList = new ArrayList<>();
            //noinspection rawtypes
            Vector vector = channelSftp.ls(remotePath);
            for (Object o : vector) {
                String fileName = ((ChannelSftp.LsEntry) o).getFilename();
                if (".".equals(fileName) || "..".equals(fileName)) {
                    continue;
                }
                SftpVo sftpVo = new SftpVo();
                sftpVo.setType(Const.STATUS_DISABLE);
                SftpATTRS attrs = channelSftp.lstat(remotePath + "/" + fileName);
                if (attrs.isDir()) {
                    fileName = fileName + "/";
                    sftpVo.setType(Const.STATUS_ENABLE);
                }
                sftpVo.setSize(readFileSize(attrs.getSize()));
                sftpVo.setPermissions(attrs.getPermissionsString());
                sftpVo.setName(fileName);
                fileList.add(sftpVo);
            }
            return fileList;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeChannelSftp(channelSftp);
        }
        return null;
    }

    /**
     * 判断文件是否存在
     *
     * @param session    session
     * @param remotePath 远程服务器路径
     * @return true 存在
     */
    public static boolean fileExist(Session session, String remotePath, String fileName) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            List<String> list = LinuxConnectionHelper.listFiles(session, remotePath);
            if (CollectionUtils.isEmpty(list)) {
                return false;
            }
            return list.contains(fileName);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeChannelSftp(channelSftp);
        }
        return false;
    }


    /**
     * 删除文件
     *
     * @param session
     * @param remotePath
     * @param fileName
     * @throws Exception
     */
    public static void removeFile(Session session, String remotePath, String fileName) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            channelSftp.cd(remotePath);
            channelSftp.rm(fileName);
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 删除文件夹
     *
     * @param session
     * @param remotePath
     * @throws Exception
     */
    public static void removeDir(Session session, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            if (remotePath.lastIndexOf("/") == remotePath.length() - 1) {
                remotePath = remotePath.substring(0, remotePath.length() - 1);
            }
            String parentDir = remotePath.substring(0, remotePath.lastIndexOf("/") + 1);
            String rmDir = remotePath.substring(remotePath.lastIndexOf("/") + 1, remotePath.length());
            channelSftp.cd(parentDir);
            channelSftp.rmdir(rmDir);
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 新建一个 exec 通道
     *
     * @param session
     * @return
     * @throws JSchException
     */
    public static ChannelExec openChannelExec(Session session) throws JSchException {
        ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
        return channelExec;
    }

    /**
     * 关闭 exec 通道
     *
     * @param channelExec
     */
    public static void closeChannelExec(ChannelExec channelExec) {
        if (channelExec != null) {
            channelExec.disconnect();
        }
    }

    /**
     * 执行 脚本 默认10秒确认执行结果
     *
     * @param session 通道session信息
     * @param cmd     执行 .sh 脚本
     * @param charset 字符格式
     * @return 执行结果（成功、失败）
     */
    public static String[] execCmd(Session session, String cmd, String charset) throws Exception {
        //打开通道
        ChannelExec channelExec = openChannelExec(session);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        channelExec.setCommand(cmd);
        channelExec.setOutputStream(out);
        channelExec.setErrStream(error);
        channelExec.connect();
        //确保能够执行完成及响应所有数据
        Thread.sleep(10000);
        String[] msg = new String[2];
        msg[0] = new String(out.toByteArray(), charset);
        msg[1] = new String(error.toByteArray(), charset);
        out.close();
        error.close();
        //关闭通道
        closeChannelExec(channelExec);
        return msg;
    }


    /**
     * 执行 脚本
     *
     * @param session      连接信息
     * @param cmd          执行 .sh 脚本
     * @param charset      字符格式,建议utf-8
     * @param sleepTimeout 最大等待时长
     * @return 执行结果（成功、失败）
     */
    public static String[] execCmd(Session session, String cmd, String charset, int sleepTimeout) throws Exception {
        //打开通道
        ChannelExec channelExec = openChannelExec(session);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        channelExec.setCommand(cmd);
        channelExec.setOutputStream(out);
        channelExec.setErrStream(error);
        channelExec.connect();
        int t = 0;
        String[] msg = new String[2];
        sleepTimeout = Math.max(sleepTimeout, 10);
        //当t大于最大任务时间后退出
        while (t < sleepTimeout) {
            msg[0] = new String(out.toByteArray(), charset);
            msg[1] = new String(error.toByteArray(), charset);
            //获取到输出的完成结果后退出
            if (StringUtils.isNotBlank(msg[0]) && msg[0].contains(SH_END)) {
                break;
            }
            //错误时，等待10秒后退出
            if (StringUtils.isNotBlank(msg[1]) && t > 10) {
                break;
            }
            //未收到正确执行的指令
            if (StringUtils.isBlank(msg[0]) && StringUtils.isBlank(msg[1]) && t > 10) {
                break;
            }
            t += 5;
            Thread.sleep(5000);
        }
        out.close();
        error.close();
        //关闭通道
        closeChannelExec(channelExec);
        return msg;
    }

    /**
     * 创建一个交互式的 shell 通道
     *
     * @param session
     * @return
     */
    public static ChannelShell openChannelShell(Session session) throws JSchException {
        ChannelShell channelShell = (ChannelShell) session.openChannel("shell");
        return channelShell;
    }

    /**
     * 关闭 shell 通道
     *
     * @param channelShell
     */
    public static void closeChannelShell(ChannelShell channelShell) {
        if (channelShell != null) {
            channelShell.disconnect();
        }
    }

    /**
     * 执行命令
     *
     * @param cmds         命令参数
     * @param session
     * @param timeout      连接超时时间
     * @param sleepTimeout 线程等待时间
     * @return
     */
    public static String execShellCmd(String[] cmds, Session session, int timeout, int sleepTimeout) throws Exception {
        //打开通道
        ChannelShell channelShell = openChannelShell(session);
        //设置输入输出流
        PipedOutputStream pipedOut = new PipedOutputStream();
        ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
        channelShell.setInputStream(new PipedInputStream(pipedOut));
        channelShell.setOutputStream(errorOut);
        channelShell.connect(timeout);
        for (String cmd : cmds) {
            pipedOut.write(cmd.getBytes("UTF-8"));
            //线程休眠，保证执行命令后能够及时返回响应数据
            Thread.sleep(sleepTimeout);
        }
        String msg = new String(errorOut.toByteArray(), "UTF-8");
        log.info(msg);
        pipedOut.close();
        errorOut.close();
        //关闭通道
        closeChannelShell(channelShell);
        return msg;
    }

    /**
     * 创建定时器，清除timeout 的 session 连接
     */
    public static void createSessionMonitor() {
        if (RUN) {
            return;
        }
        executorService.scheduleAtFixedRate(() -> {
            if (!cache.isEmpty()) {
                for (String key : cache.keySet()) {
                    //清除失效session
                    if (testSessionIsDown(key)) {
                        closeLongSessionByKey(key);
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
        RUN = true;
    }

    //文件字节大小转换成 kb、M、G 等单元
    public static String readFileSize(long size) {
        if (size <= 0) {
            return "0";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        BigDecimal bigDecimal = new BigDecimal(size / Math.pow(1024, digitGroups));
        return bigDecimal.setScale(2, BigDecimal.ROUND_HALF_UP) + " " + units[digitGroups];
    }
}
