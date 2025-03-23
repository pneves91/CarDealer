package com.cardealer.services;

import com.cardealer.models.dto.CarDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CarService {

    // Lista mockada de carros
    private List<CarDTO> carList = new ArrayList<>();

    // Construtor para adicionar carros mockados
    public CarService() {
        // Carros mockados
        carList.add(new CarDTO(1L, "Toyota", "Corolla", 20000.0, "Car Dealer Ltd."));
        carList.add(new CarDTO(2L, "Honda", "Civic", 22000.0, "Car Dealer Ltd."));
        carList.add(new CarDTO(3L, "Ford", "Fiesta", 15000.0, "Car Dealer Ltd."));
        carList.add(new CarDTO(4L, "BMW", "X3", 45000.0, "Car Dealer Ltd."));
    }

    // Método para listar todos os carros
    public List<CarDTO> getCars() {
        return carList; // Retorna a lista mockada
    }

    // Método para criar um novo carro (mockado)
    public CarDTO createCar(CarDTO carDTO) {
        carDTO.setId((long) (carList.size() + 1)); // Simula a criação de um ID
        carList.add(carDTO); // Adiciona o carro à lista mockada
        return carDTO;
    }

    // Método para obter um carro pelo ID (mockado)
    public CarDTO getCarById(Long id) {
        return carList.stream()
                .filter(car -> car.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Car not found"));
    }

    // Método para atualizar um carro (mockado)
    public CarDTO updateCar(Long id, CarDTO carDTO) {
        for (int i = 0; i < carList.size(); i++) {
            if (carList.get(i).getId().equals(id)) {
                carList.set(i, carDTO); // Atualiza o carro
                return carDTO;
            }
        }
        throw new RuntimeException("Car not found");
    }

    // Método para deletar um carro (mockado)
    public void deleteCar(Long id) {
        carList.removeIf(car -> car.getId().equals(id)); // Remove o carro da lista
    }
}