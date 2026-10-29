package br.com.fiap.minitask;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class TarefaControllerTest {
    @Test
    void deveCadastrarEListarTarefa() {
        TarefaController controller = new TarefaController();
        var criada = controller.cadastrar(new TarefaController.CriarTarefa("Estudar DevOps"));
        assertEquals(1L, criada.id());
        assertEquals("PENDENTE", criada.status());
        assertEquals(1, controller.listar().size());
    }

    @Test
    void deveRejeitarTituloVazio() {
        TarefaController controller = new TarefaController();
        var erro = assertThrows(ResponseStatusException.class,
            () -> controller.cadastrar(new TarefaController.CriarTarefa(" ")));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
    }
}
