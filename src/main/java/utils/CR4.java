package utils;

/**
 * @author charlottexiao
 */
public class CR4 {
    /**
     * s-box
     */
    private final int[] box = new int[256];

    /** PRGA 状态，必须在多个分块调用间维持 */
    private int prgaI;
    private int prgaJ;
    private boolean ksaCalled;

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
            j = (j + box[i] + key[i % len]) & 0xff;
            int swap = box[i];
            box[i] = box[j];
            box[j] = swap;
        }
        prgaI = 0;
        prgaJ = 0;
        ksaCalled = true;
    }

    /**
     * CR4-PRGA伪随机数生成算法
     * 功能:加密或解密。分块调用时状态自动维持
     *
     * @param data   加密|解密的数据
     * @param length 数据长度
     */
    public void PRGA(byte[] data, int length) {
        for (int k = 0; k < length; k++) {
            prgaI = (prgaI + 1) & 0xff;
            prgaJ = (prgaJ + box[prgaI]) & 0xff;
            int swap = box[prgaI];
            box[prgaI] = box[prgaJ];
            box[prgaJ] = swap;
            data[k] ^= box[(box[prgaI] + box[prgaJ]) & 0xff];
        }
    }
}
