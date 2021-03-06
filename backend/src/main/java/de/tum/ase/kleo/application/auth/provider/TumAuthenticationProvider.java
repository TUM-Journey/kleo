package de.tum.ase.kleo.application.auth.provider;

import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomAttr;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.tum.ase.kleo.domain.User;
import de.tum.ase.kleo.domain.UserRepository;
import de.tum.ase.kleo.domain.UserRole;
import lombok.val;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class TumAuthenticationProvider implements AuthenticationProvider {

    private static final String SHIBBOLETH_LOGIN_PAGE = "https://www.moodle.tum.de/Shibboleth.sso/Login" +
            "?providerId=https%3A%2F%2Ftumidp.lrz.de%2Fidp%2Fshibboleth" +
            "&target=https%3A%2F%2Fwww.moodle.tum.de%2Fauth%2Fshibboleth%2Findex.php";

    private static final String SHIBBOLETH_USERNAME_INPUT_NAME = "j_username";
    private static final String SHIBBOLETH_PASSWORD_INPUT_NAME = "j_password";
    private static final String SHIBBOLETH_SUBMIT_BTN_NAME = "_eventId_proceed";
    private static final String SHIBBOLETH_NOJS_SUBMIT_BTN_XPATH = "/html/body/form/noscript/div/input";
    private static final String SHIBBOLETH_ERROR_XPATH = "//p[contains(@class, \"form-error\")]";

    private static final String MOODLE_USERID_XPATH = "//*[@data-userid]/@data-userid";
    private static final String MOODLE_EDIT_PROFILE_URL = "https://www.moodle.tum.de/user/edit.php?id=%s";
    private static final String MOODLE_EDIT_PROFILE_FNAME_INPUT_NAME = "firstname";
    private static final String MOODLE_EDIT_PROFILE_SNAME_INPUT_NAME = "lastname";
    private static final String MOODLE_EDIT_PROFILE_MATRIK_INPUT_NAME = "idnumber";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final List<UserRole> userRoles = new ArrayList<>();

    public TumAuthenticationProvider(UserRepository userRepository, PasswordEncoder passwordEncoder, List<UserRole> userRoles) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userRoles.addAll(userRoles);
    }

    public TumAuthenticationProvider(UserRepository userRepository, PasswordEncoder passwordEncoder, UserRole... userRoles) {
        this(userRepository, passwordEncoder, asList(userRoles));
    }

    public TumAuthenticationProvider(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, User.DEFAULT_USER_ROLES);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        val email = authentication.getName();
        val password = authentication.getCredentials().toString();

        val shibbolethUser = fetchShibbolethUser(email, password);
        userRepository.save(shibbolethUser);

        val userGrantedAthorities = UserGrantedAuthorities.fromUserRoles(shibbolethUser.userRoles());
        return new UsernamePasswordAuthenticationToken(shibbolethUser, null, userGrantedAthorities);
    }

    private User fetchShibbolethUser(String email, String password) {
        try (val webClient = setupNewWebClient()) {
            val loginPage = (HtmlPage) webClient.getPage(SHIBBOLETH_LOGIN_PAGE);

            val usernameInput = (HtmlInput) loginPage.getElementByName(SHIBBOLETH_USERNAME_INPUT_NAME);
            val passwordInput = (HtmlInput) loginPage.getElementByName(SHIBBOLETH_PASSWORD_INPUT_NAME);
            val submitBtn = (HtmlButton) loginPage.getElementByName(SHIBBOLETH_SUBMIT_BTN_NAME);

            usernameInput.setValueAttribute(email);
            passwordInput.setValueAttribute(password);

            val noJsRedirectPage = (HtmlPage) submitBtn.click();
            val noJsRedirectSubmitBtn = (HtmlInput) noJsRedirectPage.getFirstByXPath(SHIBBOLETH_NOJS_SUBMIT_BTN_XPATH);

            val moodlePage = (HtmlPage) noJsRedirectSubmitBtn.click();

            if (moodlePage.getFirstByXPath(SHIBBOLETH_ERROR_XPATH) != null)
                throw new AuthenticationServiceException("Failed to fetch Shibboleth user " +
                        "(username and/or password are invalid)");

            val userId = ((DomAttr) moodlePage.getFirstByXPath(MOODLE_USERID_XPATH)).getValue();

            val moodleEditProfilePage = (HtmlPage) webClient.getPage(format(MOODLE_EDIT_PROFILE_URL, userId));
            val fnameInput = (HtmlInput) moodleEditProfilePage.getElementByName(MOODLE_EDIT_PROFILE_FNAME_INPUT_NAME);
            val snameInput = (HtmlInput) moodleEditProfilePage.getElementByName(MOODLE_EDIT_PROFILE_SNAME_INPUT_NAME);
            val matricInput = (HtmlInput) moodleEditProfilePage.getElementByName(MOODLE_EDIT_PROFILE_MATRIK_INPUT_NAME);

            val name = fnameInput.getValueAttribute() + " " + snameInput.getValueAttribute();
            val studentId = matricInput.getValueAttribute();
            val passwordHash = passwordEncoder.encode(password);

            return new User(email, passwordHash, userRoles, name, studentId);
        } catch (IOException e) {
            throw new AuthenticationServiceException("Failed to navigate through Shibboleth auth page", e);
        }
    }

    private static WebClient setupNewWebClient() {
        val webClient = new WebClient();
        val webClientOpts = webClient.getOptions();

        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        webClientOpts.setThrowExceptionOnFailingStatusCode(false);
        webClientOpts.setThrowExceptionOnScriptError(false);
        webClientOpts.setCssEnabled(false);
        webClientOpts.setUseInsecureSSL(true);

        webClientOpts.setCssEnabled(false);
        webClientOpts.setJavaScriptEnabled(false);

        return webClient;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
