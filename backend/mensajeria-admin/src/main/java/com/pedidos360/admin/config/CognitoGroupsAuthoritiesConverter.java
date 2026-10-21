package com.pedidos360.admin.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/** Traduce el claim "cognito:groups" a authorities ROLE_ (ej. ["ADMIN"] -> ROLE_ADMIN). */
public class CognitoGroupsAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups == null) {
            return List.of();
        }
        return groups.stream()
                .<GrantedAuthority>map(group -> new SimpleGrantedAuthority("ROLE_" + group))
                .toList();
    }
}
