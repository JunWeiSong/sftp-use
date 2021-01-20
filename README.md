# sftp-use
sftp基础使用，可拓展
#1、关于session创建
若项目中，只有一个sftp服务，可将session创建提取出来
#2、关于进度维护
若项目中上传频繁，造成频繁操作cache,可新建线程，1秒进行一次维护
