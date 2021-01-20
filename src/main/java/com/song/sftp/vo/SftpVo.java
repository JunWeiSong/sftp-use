package com.song.sftp.vo;

/**
 * @Package: com.kuke.kukey.domain.VO
 * @Description： sftp文件目录
 * @Author: SongJunWei
 * @Date: 2021/1/19
 * @Modified By:
 */
public class SftpVo {

    private String name;
    /***0文件，1目录*/
    private Integer type;

    private String size;
    /***属性：文件权限*/
    private String permissions;

    public SftpVo() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }
}
