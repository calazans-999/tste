package br.com.fiap.minitask;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/tarefas")
public class TarefaController {
    private final Map<Long, Tarefa> tarefas = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public record Tarefa(Long id, String titulo, String status) {}
    public record CriarTarefa(String titulo) {}

    @GetMapping
    public Collection<Tarefa> listar() {
        return tarefas.values();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tarefa cadastrar(@RequestBody CriarTarefa entrada) {
        if (entrada == null || entrada.titulo() == null || entrada.titulo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "titulo e obrigatorio");
        }
        long id = sequencia.incrementAndGet();
        Tarefa tarefa = new Tarefa(id, entrada.titulo(), "PENDENTE");
        tarefas.put(id, tarefa);
        return tarefa;
    }
}
