package dev.lavra.identity.web;

import dev.lavra.identity.IdentityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class MeController {

    private final IdentityService identityService;

    MeController(IdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * Profile, plan in force and consumption for the current period. Also the
     * route that materialises a brand-new user: the first call after signing in
     * at the IdP is what creates the local record.
     */
    @GetMapping("/me")
    UserProfileResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return UserProfileResponse.from(identityService.currentAccount(jwt));
    }
}
