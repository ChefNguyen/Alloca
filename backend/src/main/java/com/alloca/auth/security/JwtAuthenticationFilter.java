package com.alloca.auth.security;

import com.alloca.auth.domain.User;
import com.alloca.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        // 1. Nếu không có header hoặc không đúng định dạng "Bearer <token>", bỏ qua filter này
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            // 2. Thẩm định token và bóc tách claims
            var claims = jwtService.parseAndValidateAccessToken(token);
            UUID userId = UUID.fromString(claims.getSubject());

            // 3. Real-time active check từ Database (BR-AUTH-01)
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty() || !userOptional.get().isActive()) {
                throw new BadCredentialsException("User account is inactive or no longer exists");
            }

            User user = userOptional.get();

            // 4. Tạo UserPrincipal với đầy đủ role và departmentId để hỗ trợ phân quyền
            UserPrincipal principal = new UserPrincipal(
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    user.getDepartment() != null ? user.getDepartment().getId() : null
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 5. Nạp danh tính vào SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.warn("JWT authentication failed: {}", ex.getMessage());
            // Xóa ngữ cảnh bảo mật nếu có lỗi
            SecurityContextHolder.clearContext();
            // Chuyển giao ngoại lệ cho AuthenticationEntryPoint trả về JSON chuẩn RFC 7807 (401)
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException(ex.getMessage(), ex)
            );
        }
    }
}
