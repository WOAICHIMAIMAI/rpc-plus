package com.zheng.serializer;

import java.io.IOException;

public class TempSerializer implements Serializer{
    @Override
    public <T> byte[] serialize(T object) throws IOException {
        return new byte[0];
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) throws IOException {
        return null;
    }
}
