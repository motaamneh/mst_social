package com.motaamneh.mstsocial.core.security;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
/*  MO TAAMNEH WROTE THIS BY HIS OWN HANDS :)   used tab instead of space   */
public class VerificationCodeGenerator {
    private final SecureRandom secureRandom;

    public VerificationCodeGenerator(SecureRandom secureRandom){
        this.secureRandom = Objects.requireNonNull(
                secureRandom, "secureRandom must not be null"
        );
    }
    public String generate(){
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return "mst_"+ HexFormat.of().formatHex(randomBytes);
    }
}
