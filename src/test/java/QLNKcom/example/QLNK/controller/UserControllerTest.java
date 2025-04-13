//package QLNKcom.example.QLNK.controller;
//
//import QLNKcom.example.QLNK.config.jwt.JwtUtils;
//import QLNKcom.example.QLNK.controller.user.UserController;
//import QLNKcom.example.QLNK.model.User;
//import QLNKcom.example.QLNK.response.ResponseObject;
//import QLNKcom.example.QLNK.service.auth.AuthService;
//import QLNKcom.example.QLNK.service.user.UserService;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
//import org.springframework.context.annotation.Import;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.web.reactive.server.WebTestClient;
//import reactor.core.publisher.Mono;
//
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//@WebFluxTest(UserController.class)
//@Import(UserControllerTest.TestConfig.class)
//public class UserControllerTest {
//
//    @Autowired
//    private WebTestClient webTestClient;
//
//    @Autowired
//    private UserService userService; // Autowire directly
//
//    @Autowired
//    private AuthService authService; // Autowire directly
//
//    @Autowired
//    private JwtUtils jwtUtils;
//
//    static class TestConfig {
//        @org.springframework.context.annotation.Bean
//        public UserService userService() {
//            return mock(UserService.class);
//        }
//
//        @org.springframework.context.annotation.Bean
//        public AuthService authService() {
//            return mock(AuthService.class);
//        }
//
//        @org.springframework.context.annotation.Bean
//        public JwtUtils jwtUtils() {
//            return mock(JwtUtils.class);
//        }
//    }
//
//    @Test
//    @WithMockUser(username = "test@example.com")
//    public void testGetInfo() {
//        User mockUser = User.builder()
//                .id("123")
//                .username("testuser")
//                .password("hashedpassword")
//                .email("test@example.com")
//                .apikey("someapikey")
//                .groups(null)
//                .build();
//        when(userService.getInfo("test@example.com")).thenReturn(Mono.just(mockUser));
//
//        webTestClient.get()
//                .uri("/user/info")
//                .accept(MediaType.APPLICATION_JSON)
//                .exchange()
//                .expectStatus().isOk()
//                .expectBody(ResponseObject.class)
//                .value(response -> {
//                    assert response.getMessage().equals("Get info successfully");
//                    assert response.getStatus() == 200;
//                    User data = (User) response.getData();
//                    assert data.getId().equals("123");
//                    assert data.getUsername().equals("testuser");
//                    assert data.getEmail().equals("test@example.com");
//                    assert data.getApikey().equals("someapikey");
//                    assert data.getPassword().equals("hashedpassword");
//                    assert data.getGroups() == null;
//                });
//    }
//}
