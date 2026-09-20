package utils;

/**
 * @author charlottexiao
 */
public class CR4 {
    /**
     * s-box
     */
    private final int[] box = new int[256];

    /** PRGA 已输出的字节数,用于跨分块保持密钥流位置 */
    private int prgaPos;

    /**
     * CR4-KSA秘钥调度算法
     * 功能:生成s-box
     *
     * @param key 密钥
     */
    public void KSA(byte[] key) {
        int len = key.length;
        for (int i = 0; i < 256; i++) {
            box[i] = i;
        }
        for (int i = 0, j = 0; i < 256; i++) {
            j = (j + box[i] + (key[i % len] & 0xff)) & 0xff;
            int swap = box[i];
            box[i] = box[j];
            box[j] = swap;
        }
        prgaPos = 0;
    }

    /**
     * CR4-PRGA伪随机数生成算法
     * 功能:加密或解密。网易的变体不做s-box交换,密钥流仅由输出位置决定,周期256;
     * 按累计输出位置取模,因此任意分块切割都能得到同一段密钥流。
     *
     * @param data   加密|解密的数据
     * @param length 数据长度
     */
    public void PRGA(byte[] data, int length) {
        for (int k = 0; k < length; k++) {
            int i = (prgaPos + k + 1) & 0xff;
            int j = (box[i] + i) & 0xff;
            data[k] ^= box[(box[i] + box[j]) & 0xff];
        }
        prgaPos = (prgaPos + length) & 0xff;
    }
}
