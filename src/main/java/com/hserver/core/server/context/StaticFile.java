package com.hserver.core.server.context;

import io.netty.buffer.ByteBuf;

public class StaticFile {

    //文件大小
    private long size;
    //文件名字
    private String fileName;
    //文件后缀
    private String fileSuffix;
    //文件流
    private ByteBuf byteBuf;

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHead() {
        return MimeType.get(fileSuffix);
    }

    public void setFileType(String type) {
        this.fileSuffix = type;
    }

    public ByteBuf getByteBuf() {
        return byteBuf;
    }

    public void setByteBuf(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }
}

