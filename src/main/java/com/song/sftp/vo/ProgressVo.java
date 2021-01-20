package com.song.sftp.vo;

import lombok.Data;

/**
 * @Package: com.kuke.kukey.domain.VO
 * @Description： 上传或下载进度
 * @Author: SongJunWei
 * @Date: 2021/1/19
 * @Modified By:
 */
@Data
public class ProgressVo {
    /***当前接收的总字节数*/
    private String count;
    /***最终文件大小*/
    private String max;
    /***进度*/
    private Long percent;
    /***进度维护Key*/
    private String progressKey;

    public ProgressVo() {
    }

    public ProgressVo(String count, String max, Long percent, String progressKey) {
        this.count = count;
        this.max = max;
        this.percent = percent;
        this.progressKey = progressKey;
    }
}
