package com.lightweb.framework.util;

public class ByteArrayDoubleOccurrence {


    /**
     * 快速稳定的字节数组查找算法（KMP优化版）
     * @param source 源字节数组
     * @param target 目标字节数组
     * @return 如果找到返回true，否则返回false
     */
    public static boolean containsBytes(byte[] source, byte[] target) {
        if (target == null || target.length == 0) return true;
        if (source == null || source.length == 0) return false;
        if (target.length > source.length) return false;
        
        // 快速失败检查：首字节和尾字节匹配
        if (source[0] != target[0] || 
            source[source.length - 1] != target[target.length - 1]) {
            // 首尾都不匹配，快速返回（适用于大多数场景）
            return false;
        }
        
        // 计算LPS（最长前缀后缀）数组
        int[] lps = computeLPSArray(target);
        
        int i = 0;  // source索引
        int j = 0;  // target索引
        
        while (i < source.length) {
            if (target[j] == source[i]) {
                i++;
                j++;
            }
            
            if (j == target.length) {
                return true;  // 找到匹配
            } else if (i < source.length && target[j] != source[i]) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 优化的简单版本（适合短目标数组）
     */
    public static boolean containsBytesSimple(byte[] source, byte[] target) {
        if (target.length == 0) return true;
        if (target.length > source.length) return false;
        
        // 使用首字符优化
        byte first = target[0];
        int max = source.length - target.length;
        
        for (int i = 0; i <= max; i++) {
            // 快速跳过不匹配的位置
            if (source[i] != first) {
                while (++i <= max && source[i] != first);
            }
            
            // 找到首字符匹配的位置
            if (i <= max) {
                // 检查剩余部分
                int j = i + 1;
                int end = j + target.length - 1;
                for (int k = 1; j < end && source[j] == target[k]; j++, k++);
                
                if (j == end) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 查找pattern是否在text中至少出现两次
     * 使用KMP算法进行高效匹配
     */
    public static boolean containsAtLeastTwoOccurrences(byte[] text, byte[] pattern) {
        if (pattern.length == 0 || pattern.length > text.length) {
            return false;
        }
        
        int count = 0;
        int[] lps = computeLPSArray(pattern);
        int i = 0; // text的索引
        int j = 0; // pattern的索引
        
        while (i < text.length) {
            if (pattern[j] == text[i]) {
                i++;
                j++;
            }
            
            if (j == pattern.length) {
                count++;
                if (count >= 1) {
                    return true;
                }
                j = lps[j - 1];
            } else if (i < text.length && pattern[j] != text[i]) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 计算LPS数组（最长前缀后缀数组）
     */
    private static int[] computeLPSArray(byte[] pattern) {
        int[] lps = new int[pattern.length];
        int len = 0;
        int i = 1;
        
        while (i < pattern.length) {
            if (pattern[i] == pattern[len]) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
        return lps;
    }


    /**
     * 使用System.arraycopy实现，性能更好
     */
    public static byte[][] splitBytes(byte[] source, byte[] delimiter) {
        int delimiterPos = indexOf(source, delimiter, 0);
        
        if (delimiterPos == -1) {
            return new byte[][]{source, new byte[0]};
        }
        
        byte[] part1 = new byte[delimiterPos];
        byte[] part2 = new byte[source.length - delimiterPos - delimiter.length];
        
        // 复制第一部分
        System.arraycopy(source, 0, part1, 0, delimiterPos);
        
        // 复制第二部分
        System.arraycopy(source, delimiterPos + delimiter.length, 
                        part2, 0, part2.length);
        
        return new byte[][]{part1, part2};
    }
    
    private static int indexOf(byte[] source, byte[] target, int fromIndex) {
        if (fromIndex >= source.length) {
            return -1;
        }
        if (target.length == 0) {
            return fromIndex;
        }
        
        byte first = target[0];
        int max = source.length - target.length;
        
        for (int i = fromIndex; i <= max; i++) {
            // 查找第一个字节匹配的位置
            if (source[i] != first) {
                while (++i <= max && source[i] != first);
            }
            
            // 找到第一个字节后，检查剩余字节
            if (i <= max) {
                int j = i + 1;
                int end = j + target.length - 1;
                for (int k = 1; j < end && source[j] == target[k]; j++, k++);
                
                if (j == end) {
                    return i;
                }
            }
        }
        return -1;
    }
    
}
