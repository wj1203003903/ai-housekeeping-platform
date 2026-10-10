package com.anxin.home.genai.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ComplianceGuard {
    private static final Pattern SECRET = Pattern.compile(
        "(?i)(密码|口令|password|api[ _-]?key|secret)\\s*[：:=]\\s*\\S+");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
        "(?i)(忽略|绕过|泄露|显示).{0,12}(系统提示|安全规则|system prompt|权限校验)");

    public void validateInput(String input) {
        if (SECRET.matcher(input).find() || CARD.matcher(input.replace(" ", "")).find())
            throw new IllegalArgumentException("请勿在对话中提交密码、密钥或银行卡号");
        if (PROMPT_INJECTION.matcher(input).find())
            throw new SecurityException("请求包含绕过系统安全规则的指令");
    }
}
