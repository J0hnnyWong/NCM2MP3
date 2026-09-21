package service.command;

import executor.AsyncTaskExecutor;
import service.ConvertOptions;
import service.Converter;
import service.command.common.BaseCommand;
import service.tag.TagMode;
import utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * @author charlottexiao
 */
public class ConvertCommand extends BaseCommand {

    @Override
    public void handle(List<String> params) {
        TagMode tagMode = TagMode.NCM;
        boolean reEncodeWithFfmpeg = false;
        boolean keepFlacWithMp3 = false;
        ArrayList<String> paths = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            String param = params.get(i);
            if (("-m".equals(param) || "--mode".equals(param)) && i + 1 < params.size()) {
                tagMode = TagMode.from(params.get(++i));
            } else if ("-f".equals(param) || "--ffmpeg".equals(param)) {
                reEncodeWithFfmpeg = true;
            } else if ("-k".equals(param) || "--keep-flac".equals(param)) {
                keepFlacWithMp3 = true;
            } else {
                paths.add(param);
            }
        }
        System.out.printf("Tag mode is set to: %s%n", tagMode.name());
        System.out.printf("ffmpeg re-encode: %s%n", reEncodeWithFfmpeg);
        System.out.printf("keep flac alongside mp3: %s%n", keepFlacWithMp3);

        //File outputPath = new File("." + File.separator + "output");
        File outputPath = new File("output");
        if(!outputPath.mkdir()){
            if(outputPath.isDirectory()){
                //文件夹已存在
                System.out.println("output文件夹已存在，将复用");
            }
            else{
                //存在output同名非文件夹
                Random random = new Random();
                outputPath = new File("output"+ random.nextInt());
            }
        }
        System.out.printf("Output dir is set to: %s%n", outputPath.getAbsolutePath());

        ArrayList<File> files = new ArrayList<>();
        paths.forEach((param) -> Utils.listAllFiles(files, new File(param)));

        //中途有修改过outputPath的对象引用，要copy一份final才能传入lambda
        File finalOutputPath = outputPath;
        ConvertOptions options = new ConvertOptions(tagMode, reEncodeWithFfmpeg, keepFlacWithMp3);
        Converter converter = new Converter();
        List<Future<Boolean>> futures = files.stream()
                .map(inputFile -> AsyncTaskExecutor.submit(() -> converter.ncm2Mp3(inputFile.getAbsolutePath(), finalOutputPath.getAbsolutePath(), options)))
                .collect(Collectors.toList());
        Utils.waitForAllTask(futures, result -> result);
        System.exit(0);
    }
}
