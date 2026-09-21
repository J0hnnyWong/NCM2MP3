# NCM2MP3
网易云ncm音乐格式转换为mp3音乐格式工具

## 环境准备
- JDK 17（lombok 1.18.22 不兼容过高的 JDK）
- 依赖构建工具 Maven
- ffmpeg（仅在需要重编码为 MP3 时需要，放在 PATH 里）
- 集成开发环境 IDEA(插件支持:Lombok)

## 运行说明

两个工具都同时支持 macOS 与 Windows，代码里没有任何平台相关假设（路径一律用 `File.separator`，ffmpeg 从 PATH 探测）：

| 平台 | NCM → MP3（主项目） | FLAC → MP3 + 元信息回写（flac-converter） |
| :--: | :-- | :-- |
| Windows | 双击或执行 `run.bat`（可加 `build` 强制重建，或传 `-c -f <path>`） | `run-flac.bat` |
| macOS / Linux | `./run.sh`（或 `make run`） | `./run-flac.sh`（或 `make flac-run`） |
| 直接跑 jar | `java -jar target/NCM2MP3-3.1.0.jar` | `java -jar flac-converter/target/flac-converter-1.0.0.jar` |

依赖安装：macOS `make setup-mac`（brew 装 openjdk@17 / maven / ffmpeg），Windows `make setup-windows` 会列出对应的 winget 命令。

```text

1. 使用NCM2MP3.jar运行图形界面(只需要准备jdk环境便可以):命令行中在该jar包的目录下执行`java -jar NCM2MP3.jar`
2. 用源代码运行:在环境配置好后,执行入口为`main.java`
3. 命令行相关操作`java -jar NCM2MP3.jar [command]`
Usage: java -jar NCM2MP3.jar [command]
If don't add command, there will open NCM2MP3 GUI directly
[Command List]
-v,-view                      : open NCM View GUI(default command)
-c,--convert [path] ...       : convert NCM File in path to ./output directory
-m,--mode <ncm|path>          : tag mode used with -c, default ncm
-f,--ffmpeg                   : re-encode to MP3 320kbps CBR with ffmpeg
-k,--keep-flac                : with -f, also keep the original FLAC in the output dir
-h,-help                      : Help about any command
```

不带 `-f` 时输出的是解密出的原始音频（扩展名按解密后的真实格式确定，NCM 里写的 format 只是参考）；带 `-f` 时输出目录里默认只有 MP3，
中间解密的 FLAC 只存在于系统临时目录，转换失败不会留下半成品；需要无损原件时加 `-k`（界面里对应"高级设置 → 转 MP3 的同时保留原始 FLAC"）。

转换时可以通过`-m/--mode`选择标签信息来源(默认为`ncm`,图形界面底部也可以直接选择模式):

```text
-m,--mode <ncm|path>
  ncm : 使用NCM文件内置的标题/歌手/专辑/封面信息(原有行为)
  path: 歌手与专辑按 歌手/专辑/歌曲 目录结构获取,
        封面取上级 meta 文件夹中的 track-{musicId} 图片(找不到时回退到NCM内置封面)

示例: java -jar NCM2MP3.jar -c -m path /Users/johnny/Music/网易云音乐
```


## flac-converter（同目录下的独立子项目）

只做一件事：递归扫描一个目录里的 FLAC，转成 320kbps CBR 的 MP3，并把源文件自带的全部元信息（标题/歌手/专辑/专辑歌手/年份/流派/音轨/碟号/作曲/作词/备注/歌词 + 封面）写回 MP3。
与主项目代码完全独立（`flac-converter/` 有自己的 pom 与 jar），界面为 选输入目录 → 扫描 → 选输出目录 → 开始转换 / 清空列表，输出保持源目录层级、同名 `.mp3`。

几点实现约定：
- 扫描按文件头 `fLaC` 判断格式，改名成 `.flac` 的其他格式会被跳过并在状态栏计数（这类文件在真实曲库里很常见）。
- 音频通过标准输入分块喂给 ffmpeg，整首不进堆；ffmpeg 的输出由独立线程持续抽干，否则管道写满会双向死锁（批量转换"卡住"的根因）。
- 封面固定写成"前封面"类型（部分便携播放器只认这一种），歌词来自音频内标签或同目录 `.lrc`（含网易云 `[ar:]`/署名行）。
- 年份与流派若源文件本身没有，则不会凭空补（本地 NCM/FLAC/LRC 里确实都没有这两项）。

## 原理说明
  NCM格式是网易云音乐特有的音乐格式,这种音乐格式用到AES,RC4的加密算法对普通的音乐格式(如MP3,FLAC)进行加密,若要了解该加密过程,最好的方法就是知道起格式图,以及加密的原理(可以参考笔记`密码学.md`).

## 现在简述一下加密的过程
|          信息          |             大小              | 备注                                                         |
| :--------------------: | :---------------------------: | :----------------------------------------------------------- |
|      Magic Header      |           10 bytes            | 跳过                              |
|       KEY Length       |            4 bytes            | 用AES128加密RC4密钥后的长度(小端字节排序,无符号整型)         |
| KEY From AES128 Decode | KEY Length(其实就是128 bytes) | 用AES128加密的RC4密钥(注意:1.按字节对0x64异或2.AES解密(其中PKCS5Padding填充模式会去除末尾填充部分;)3.去除前面`neteasecloudmusic`17个字节; |
|      Mata Length       |            4 bytes            | Mata的信息的长度(小端字节排序,无符号整型)                    |
|    Mata Data(JSON)     |          Mata Length          | JSON的格式的Mata的信息(注意:1.按字节对0x63异或;2.去除前面`163 key(Don't modify):`22个字节;3.Base64进行decode;4.AES解密;5.去除前面`music:`6个字节后获得JSON) |
|       CRC校验码        |            4 bytes            | 跳过                                                         |
|          Gap           |            5 bytes            | 跳过                                                         |
|       Image Size       |            4 bytes            | 图片大小                                                     |
|         Image          |          Image Size           | 图片数据                                                     |
|       Music Data       |               -               | RC4-KSA生成s盒,RC4-PRGA解密                                  |

## 项目构成说明
- executor:控制管理
  - ConvertTask.java 对应每一个音乐转换的任务(消费者),状态回写一律切回事件分发线程
  - AsyncTaskExecutor.java 线程池,固定线程数 `min(4, CPU核数/2)` + 无界队列,保证转换不会退回调用者线程(否则点"开始转换"会把界面卡死)
- service:音乐格式转换核心功能实现
  - Converter.java 将NCM音乐解密拆分(==如果想快速看懂这个项目:建议从这个类开始看==), 将分析的各个数据整合到一起
  - Interpreter: 命令行参数解析器(策略模式分配命令处理)
    - ConvertCommand,HelpCommand,ViewCommand: 现在支持的3种命令
- mime 封装的数据类型
  - MATA.java 音乐头部信息
  - NCM.java 音乐输入输出信息等基本信息
- utils 工具类
  - AES.java AES解密的实现(ECB加密模式,PKCS5Padding填充模式)
  - RC4.java RC4解密的实现(这算法本质就是打乱顺序,需要注意的byte是1个字节且无符号的,以及对其取整操作(&0xff))
  - Utils.java 杂七杂八的工具(有注释说明)
- View 视图
  - view.java 用的Swing做的视图(Flatlaf这个jar包中有皮肤,所以看起来还不错.以后有机会学学javaFX..)
-main.java 

## 效果
- 打开界面

![](https://github.com/charlotte-xiao/NCM2MP3/blob/master/image/picture1.png)

- 准备转换

![](https://github.com/charlotte-xiao/NCM2MP3/blob/master/image/picture2.png)

- 转换成功

![](https://github.com/charlotte-xiao/NCM2MP3/blob/master/image/picture3.png)

## 更多
- 密码学.md:关于密码学相关知识不懂的可以查看该文档

## 通知
该工具是在大学期间为了学习密码学的时候，想通过实践的方式深入了解，通过`ncmdump`的版本，用Java改写的。现在工作了，由于时间有限，本工具不会继续更新，希望大家谅解～

此工具仅供学习用途，请勿使用其盈利！
