package com.aicourse.controller;

import com.aicourse.entity.Note;
import com.aicourse.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NoteController 接口测试（补齐 41 个 Controller 中唯一的测试覆盖缺口）。
 * 覆盖 6 个端点：认证后 200 + 委托校验（jsonPath/verify），未认证 401，STUDENT 角色可访问。
 */
@WebMvcTest(NoteController.class)
class NoteControllerTest extends ControllerTestBase {

    @MockitoBean
    private NoteService noteService;

    private Note sampleNote(Long id) {
        Note n = new Note();
        n.setId(id);
        n.setUserId(1L);
        n.setTitle("笔记标题");
        n.setContent("笔记内容");
        return n;
    }

    @Test
    @DisplayName("GET /notes - 认证后返回200且列表透传，委托 listNotes")
    void list_authorized_returns_200() throws Exception {
        when(noteService.listNotes(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(sampleNote(1L)));
        mockMvc.perform(get("/notes").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(noteService).listNotes(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("GET /notes - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /notes/{id} - 认证后返回200且单条透传")
    void getOne_authorized_returns_200() throws Exception {
        when(noteService.getOwned(1L, 9L)).thenReturn(sampleNote(9L));
        mockMvc.perform(get("/notes/9").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));
        verify(noteService).getOwned(eq(1L), eq(9L));
    }

    @Test
    @DisplayName("POST /notes - 认证后返回200且委托 createOrUpdate")
    void create_authorized_returns_200() throws Exception {
        when(noteService.createOrUpdate(eq(1L), any())).thenReturn(sampleNote(1L));
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新笔记\",\"content\":\"内容\",\"courseId\":2}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("笔记标题"));
        verify(noteService).createOrUpdate(eq(1L), any());
    }

    @Test
    @DisplayName("POST /notes - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /notes/{id} - 认证后返回200且委托 updateOwned")
    void update_authorized_returns_200() throws Exception {
        when(noteService.updateOwned(eq(1L), eq(9L), any())).thenReturn(sampleNote(9L));
        mockMvc.perform(put("/notes/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改标题\",\"content\":\"改内容\"}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(noteService).updateOwned(eq(1L), eq(9L), any());
    }

    @Test
    @DisplayName("DELETE /notes/{id} - 认证后返回200且委托 deleteOwned")
    void delete_authorized_returns_200() throws Exception {
        doNothing().when(noteService).deleteOwned(1L, 9L);
        mockMvc.perform(delete("/notes/9").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(noteService).deleteOwned(eq(1L), eq(9L));
    }

    @Test
    @DisplayName("PATCH /notes/{id}/favorite - 认证后返回200且委托 setFavorite")
    void toggleFavorite_authorized_returns_200() throws Exception {
        when(noteService.setFavorite(eq(1L), eq(9L), anyBoolean())).thenReturn(sampleNote(9L));
        mockMvc.perform(patch("/notes/9/favorite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(noteService).setFavorite(eq(1L), eq(9L), eq(true));
    }
}
