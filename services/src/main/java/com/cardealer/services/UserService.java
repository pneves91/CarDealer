package com.cardealer.services;

import com.cardealer.models.dto.UserDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    // Lista mockada de usuários
    private List<UserDTO> userList = new ArrayList<>();

    // Construtor para adicionar usuários mockados
    public UserService() {
        // Usuários mockados
        userList.add(new UserDTO(1L, "John Doe", "john.doe@example.com", "MANAGER", "Car Dealer Ltd."));
        userList.add(new UserDTO(2L, "Jane Doe", "jane.doe@example.com", "MANAGER", "Car Dealer Ltd."));
        userList.add(new UserDTO(3L, "Alice Smith", "alice.smith@example.com", "MANAGER", "Car Dealer Ltd."));
        userList.add(new UserDTO(4L, "Bob Johnson", "bob.johnson@example.com", "MANAGER", "Car Dealer Ltd."));
    }

    // Método para listar todos os usuários
    public List<UserDTO> getUsers() {
        return userList; // Retorna a lista mockada de usuários
    }

    // Método para criar um novo usuário (mockado)
    public UserDTO createUser(UserDTO userDTO) {
        userDTO.setId((long) (userList.size() + 1)); // Simula a criação de um ID
        userList.add(userDTO); // Adiciona o novo usuário à lista mockada
        return userDTO;
    }

    // Método para obter um usuário pelo ID (mockado)
    public UserDTO getUserById(Long id) {
        return userList.stream()
                .filter(user -> user.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Método para atualizar um usuário (mockado)
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).getId().equals(id)) {
                userList.set(i, userDTO); // Atualiza o usuário na lista
                return userDTO;
            }
        }
        throw new RuntimeException("User not found");
    }

    // Método para deletar um usuário (mockado)
    public void deleteUser(Long id) {
        userList.removeIf(user -> user.getId().equals(id)); // Remove o usuário da lista
    }
}