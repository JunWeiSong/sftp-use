package com.song.sftp.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.song.sftp.vo.ProgressVo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @Package: com.kuke.batch.utils
 * @Description： 上传下载进度维护
 * @Author: SongJunWei
 * @Date: 2021/1/19
 * @Modified By:
 */
public class SftpProgressGuava {

    private static Logger log = LoggerFactory.getLogger(SftpProgressGuava.class);

    private static Cache<String, ProgressVo> progressGuava
            = CacheBuilder.newBuilder()
            .concurrencyLevel(8)
            //支持token过期设置,10分钟内有效
            .expireAfterWrite(600, TimeUnit.SECONDS)
            .initialCapacity(10)
            .maximumSize(1000)
            .recordStats()
            .build();

    public static void put(String progressKey,ProgressVo progress){
        if(StringUtils.isBlank(progressKey)){
            return;
        }

        try {
            progressGuava.put(progressKey,progress);
        }catch (Exception e){
            log.error("进度缓存存入失败,progressKey:{},progress:{}",progressKey,progress.toString(),e);
        }
    }

    public static ProgressVo get(String progressKey){
        if(StringUtils.isBlank(progressKey)){
            return null;
        }
        try {
            return progressGuava.getIfPresent(progressKey);
        }catch (Exception e){
            log.error("进度缓存获取失败,progressKey:{}",progressKey,e);
        }
        return null;
    }

}

