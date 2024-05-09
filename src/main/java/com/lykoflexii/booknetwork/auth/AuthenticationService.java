package com.lykoflexii.booknetwork.auth;

import com.lykoflexii.booknetwork.email.EmailService;
import com.lykoflexii.booknetwork.email.EmailTemplateName;
import com.lykoflexii.booknetwork.role.RoleRepository;
import com.lykoflexii.booknetwork.security.JwtService;
import com.lykoflexii.booknetwork.user.Token;
import com.lykoflexii.booknetwork.user.TokenRepository;
import com.lykoflexii.booknetwork.user.User;
import com.lykoflexii.booknetwork.user.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserRepository userRepository;
  private final TokenRepository tokenRepository;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final EmailService emailService;
  @Value("${application.mailing.frontend.activation-url}")
  private String activationUrl;

  public void register(RegistrationRequest request) throws MessagingException {
    var userRole = roleRepository.findByName("USER")
            .orElseThrow(() -> new IllegalStateException("ROLE USER was not initialized."));

    var user = User
            .builder()
            .firstname(request.getFirstname())
            .lastname(request.getLastname())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .accountLocked(false)
            .enabled(false)
            .roles(List.of(userRole))
            .build();

    userRepository.save(user);
    sendValidationEmail(user);
  }

  private void sendValidationEmail(User user) throws MessagingException {
    var newToken = generateAndSaveActivationToken(user);

    emailService.sendEmail(
            user.getEmail(),
            user.fullName(),
            EmailTemplateName.ACTIVATE_ACCOUNT,
            activationUrl,
            newToken,
            "Account Activation"
    );
  }

  private String generateAndSaveActivationToken(User user) {
    String generatedToken = generateActivationCode(6);
    var token = Token
            .builder()
            .token(generatedToken)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .user(user)
            .build();

    tokenRepository.save(token);
    return generatedToken;
  }

  private String generateActivationCode(int length) {
    String charaters = "0123456789";
    StringBuilder codeBuilder = new StringBuilder();
    SecureRandom secureRandom = new SecureRandom();

    for (int i = 0; i < length; i++) {
      int randomIndex = secureRandom.nextInt(charaters.length());
      codeBuilder.append(charaters.charAt(randomIndex));
    }
    return codeBuilder.toString();
  }

  public AuthenticationResponse authenticate(AuthenticationRequest request) {
    var auth = authenticationManager
            .authenticate(new UsernamePasswordAuthenticationToken(
                    request.getEmail(), request.getPassword()
            ));

    var claims = new HashMap<String, Object>();
    var user = ((User) auth.getPrincipal());

    claims.put("fullName", user.fullName());

    var jwtToken = jwtService.generateToken(claims, user);

    return AuthenticationResponse
            .builder()
            .token(jwtToken)
            .build();
  }

  public void activateAccount(String token) throws MessagingException {
    Token savedToken = tokenRepository.findByToken(token).orElseThrow(() -> new RuntimeException("Invalid token."));
    if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
      sendValidationEmail(savedToken.getUser());
      throw new RuntimeException("Activation token has expired. A new token has been sent to the same email address.");
    }
    var user = userRepository.findById(savedToken.getUser().getId())
            .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    user.setEnabled(true);
    userRepository.save(user);
    savedToken.setValidatedAt(LocalDateTime.now());
    tokenRepository.save(savedToken);
  }
}
