package com.song.sftp.utils;

import com.jcraft.jsch.SftpProgressMonitor;
import com.song.sftp.vo.ProgressVo;

/**
 * <b><断点续传></b>
 * 进度监控器-JSch每次传输一个数据块，就会调用count方法来实现主动进度通知<br/>,Runnable
 */
public class MyProgressMonitor implements SftpProgressMonitor {

    private long count = 0;     //当前接收的总字节数
    private long max;       //最终文件大小
    private long percent = -1;  //进度
    private String progressKey; //进度维护Key

    public MyProgressMonitor(Long fileSize, String progressKey){
        this.max = fileSize;
        this.progressKey = progressKey;
    }

    /**
     * 当每次传输了一个数据块后，调用count方法，count方法的参数为这一次传输的数据块大小
     */
    @Override
    public boolean count(long count) {
        this.count += count;
        if (percent >= this.count * 100 / max) {
            return true;
        }
        percent = this.count * 100 / max;
        //维护查询进度
        maintainProgress();
        System.out.println("Completed " + this.count + "(" + percent+ "%) out of " + max + ".");
        return true;
    }

    /**
     * 当传输结束时，调用end方法
     */
    @Override
    public void end() {
        System.out.println("Transferring done.");
    }

    /**
     * 当文件开始传输时，调用init方法
     */
    @Override
    public void init(int op, String src, String dest, long max) {
        if (op == SftpProgressMonitor.PUT) {
            System.out.println("Upload file begin.");
        } else {
            System.out.println("Download file begin.");
        }
        this.count = 0;
        this.percent = -1;
    }


    /**
     * 维护进度
     */
    public void maintainProgress(){
        ProgressVo progressVo = SftpProgressGuava.get(progressKey);
        if(progressVo==null){
            return;
        }
        progressVo.setCount(LinuxConnectionHelper.readFileSize(this.count));
        progressVo.setPercent(this.percent);

        SftpProgressGuava.put(progressKey,progressVo);
    }
}