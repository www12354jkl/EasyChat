package com.faust.easychat.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailUtil {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.mail.username}")
    private String mailFrom;

    private static final String CODE_PREFIX = "email:code:";
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final Random RANDOM = new Random();

    /**
     * 生成6位随机验证码
     */
    public String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }

    /**
     * 发送验证码邮件并存入 Redis
     *
     * @param to   收件人邮箱
     * @param code 验证码
     */
    public void sendVerifyCode(String to, String code) {
        String redisKey = CODE_PREFIX + to;
        redisTemplate.opsForValue().set(redisKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("【EasyChat】邮箱验证码");
            helper.setText(buildHtml(code), true);
            mailSender.send(message);
            log.info("验证码已发送至 {}", to);
        } catch (MessagingException e) {
            log.error("发送验证码邮件失败: {}", to, e);
            throw new RuntimeException("邮件发送失败");
        }
    }

    /**
     * 校验验证码
     *
     * @param email 用户邮箱
     * @param code  用户输入的验证码
     * @return true-验证成功 false-验证码错误或已过期
     */
    public boolean verifyCode(String email, String code) {
        String redisKey = CODE_PREFIX + email;
        String cachedCode = redisTemplate.opsForValue().get(redisKey);
        if (cachedCode != null && cachedCode.equals(code)) {
            redisTemplate.delete(redisKey);
            return true;
        }
        return false;
    }

    /**
     * 检查验证码是否已过期
     */
    public boolean isCodeExpired(String email) {
        return Boolean.FALSE.equals(redisTemplate.hasKey(CODE_PREFIX + email));
    }

    /**
     * 聊天气泡 logo（SVG 转 base64 data URI，邮件客户端不支持内联 svg，图片最稳）
     */
    private static final String LOGO_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"#07C160\" d=\"M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z\"/></svg>";

    private static final String LOGO_DATA_URI = "data:image/svg+xml;base64,"
            + Base64.getEncoder().encodeToString(LOGO_ICON.getBytes(StandardCharsets.UTF_8));

    private String buildHtml(String code) {
        return """
                <div style="font-family:'PingFang SC','Microsoft YaHei',Arial,sans-serif;background:#EAF7EF;padding:40px 16px;">
                    <div style="max-width:520px;margin:0 auto;background:#FFFFFF;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(7,193,96,0.08);">

                        <!-- 品牌头部 -->
                        <div style="background:linear-gradient(135deg,#07C160,#059e50);padding:28px 32px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">
                                <tr>
                                    <td style="vertical-align:middle;">
                                        <img src="%s" width="36" height="36" style="display:inline-block;vertical-align:middle;" alt="EasyChat" />
                                    </td>
                                    <td style="padding-left:12px;vertical-align:middle;">
                                        <span style="font-size:22px;font-weight:700;color:#FFFFFF;letter-spacing:1px;">EasyChat</span>
                                    </td>
                                </tr>
                            </table>
                        </div>

                        <!-- 正文 -->
                        <div style="padding:32px;">
                            <p style="margin:0 0 6px;font-size:16px;color:#1A1A1A;font-weight:600;">你好！</p>
                            <p style="margin:0 0 24px;font-size:14px;color:#8A8A8A;line-height:1.6;">你正在进行邮箱验证，本次验证码为：</p>

                            <!-- 验证码 -->
                            <div style="background:#F2F8F4;border:1px dashed #9BE2BC;border-radius:8px;padding:20px;text-align:center;margin-bottom:24px;">
                                <span style="font-size:36px;font-weight:700;letter-spacing:8px;color:#07C160;">%s</span>
                            </div>

                            <p style="margin:0 0 16px;font-size:13px;color:#999999;line-height:1.6;">
                                验证码 %d 分钟内有效，为保护你的账号安全，请勿泄露给他人。
                            </p>

                            <hr style="border:none;border-top:1px solid #F0F0F0;margin:0 0 16px;">

                            <p style="margin:0;font-size:12px;color:#B2B2B2;line-height:1.6;">如非本人操作，请忽略此邮件，并检查账号安全。</p>
                        </div>

                        <!-- 页脚 -->
                        <div style="background:#FAFAFA;padding:14px 32px;text-align:center;">
                            <span style="font-size:11px;color:#C0C0C0;">EasyChat · 让沟通更简单</span>
                        </div>
                    </div>
                </div>
                """.formatted(LOGO_DATA_URI, code, CODE_EXPIRE_MINUTES);
    }



}
