package com.hrm.controller;

import com.hrm.service.AdminAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAccessCatalogControllerTest {
    @Test
    void roleAndPermissionCreationEndpointsAreUnavailable() throws Exception {
        var service = mock(AdminAccessService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new AdminRoleController(service), new AdminPermissionController(service)).build();

        mvc.perform(post("/api/admin/roles").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/admin/permissions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/admin/roles/1").contentType(MediaType.APPLICATION_JSON).content("{\"roleName\":\"New\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/admin/roles/1/permissions").contentType(MediaType.APPLICATION_JSON).content("{\"permissionIds\":[]}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/roles/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/admin/permissions/1")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
