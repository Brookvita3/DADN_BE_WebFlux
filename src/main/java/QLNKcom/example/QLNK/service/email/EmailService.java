package QLNKcom.example.QLNK.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String emailUsername;

    public Mono<Void> sendEmail(String to, String subject, String text) {
        return Mono.fromRunnable(() -> {
                    try {
                        SimpleMailMessage message = new SimpleMailMessage();
                        message.setTo(to);
                        message.setSubject(subject);
                        message.setText(text);
                        message.setFrom(emailUsername); // Replace with your sender email
                        javaMailSender.send(message);
                        log.info("Email sent to {} with subject: {}", to, subject);
                    } catch (Exception e) {
                        log.error("Failed to send email to {}: {}", to, e.getMessage());
                        throw new RuntimeException("Email sending failed", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic()) // Run on a thread pool for I/O tasks
                .then();
    }
}
