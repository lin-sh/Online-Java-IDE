package com.example.online_java_ide.utils;

public class ClassModifier {
    // 常量池起始偏移
    private static final int CONSTANT_POOL_COUNT_INDEX = 8;

    // CONSTANT_UTF8_INFO常量的tag标志
    private static final int CONSTANT_UTF8_INFO = 1;

    // 常量池中11中常量所占的长度，CONSTANT_ITEM_LENGTH[tag]表示对应tag的常量所占的长度
    private static final int[] CONSTANT_ITEM_LENGTH = {-1, -1, -1, 5, 5, 9, 9, 3, 3, 5, 5, 5, 5};

    private static final int u1 = 1;
    private static final int u2 = 2;

    // class文件字节码
    private byte[] classByte;

    public ClassModifier(byte[] classByte) {
        this.classByte = classByte;
    }

    // 获取常量池中常量的数量
    public int getConstantPoolCount() {
        return ByteUtils.byte2Int(classByte, CONSTANT_POOL_COUNT_INDEX, u2);
    }

    // 修改常量池中CONSTANT_UTF8_INFO常量的内容
    public byte[] modifyConstantUTF8Value(String oldStr, String newStr) {
        int cpc = getConstantPoolCount();
        int offset = CONSTANT_POOL_COUNT_INDEX + u2;
        for (int i = 1; i < cpc; ++i) {
            int tag = ByteUtils.byte2Int(classByte, offset, u1);
            if (tag == CONSTANT_UTF8_INFO) {
                int len = ByteUtils.byte2Int(classByte, offset + u1, u2);
                offset += (u1 + u2);
                String str = ByteUtils.byte2String(classByte, offset, len);
                if (str.equals(oldStr)) {
                    byte[] newStrBytes = ByteUtils.string2Byte(newStr);
                    byte[] newStrLen = ByteUtils.int2Byte(newStrBytes.length, u2);

                    classByte = ByteUtils.byteReplace(classByte, offset - u2, u2, newStrLen);
                    classByte = ByteUtils.byteReplace(classByte, offset, len, newStrBytes);

                    return classByte;
                } else {
                    offset += len;
                }
            } else {
                offset += CONSTANT_ITEM_LENGTH[tag];
            }
        }
        return classByte;
    }
}
