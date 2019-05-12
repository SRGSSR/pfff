package ch.srgssr.playfff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.Collections;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
@Configuration
@EnableWebSecurity
public class AuthenticationConfig extends WebSecurityConfigurerAdapter {

    private String user;
    private String password;

    public AuthenticationConfig(
            @Value("${PFFF_USER:}") String user,
            @Value("${PFFF_PASSWORD:}") String password) {

        this.user = user;
        this.password = password;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/api/v1/update/check", "/api/v1/whatisnew/text", "/api/v1/whatisnew/html", "/api/v1/version", "/api/v*/playlist/**", "/webjars/bootstrap/**", "/webjars/bootstrap/**", "/webjars/jquery/**", "/webjars/font-awesome/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .permitAll()
                .and()
                .logout()
                .permitAll();
        http
                .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web
                .ignoring()
                .antMatchers("/api/v1/deeplink/parse_play_url.js")
                .antMatchers(HttpMethod.POST, "/api/v1/deeplink/report");
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        if (user == null || user.length() == 0 || password == null || password.length() == 0) {
            return null;
        }

        UserDetails userDetails =
                User.withUsername(user)
                        .password(password)
                        .roles("USER")
                        .build();

        return new InMemoryUserDetailsManager(Collections.singleton(userDetails));
    }
}
