package com.motaamneh.mstsocial.server;

import com.motaamneh.mstsocial.server.admin.TenantKeyCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class MstSocialApplication {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("tenant-key")) {
            System.exit(TenantKeyCommand.run(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        SpringApplication.run(MstSocialApplication.class, args);
    }

}
