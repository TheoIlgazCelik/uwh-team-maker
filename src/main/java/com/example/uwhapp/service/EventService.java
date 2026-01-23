package com.example.uwhapp.service;

import com.example.uwhapp.model.Event;
import com.example.uwhapp.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EventService {
    private final EventRepository eventRepository;

    public EventService(EventRepository repo) {
        this.eventRepository = repo;
    }

    public Event create(Event e) {
        return eventRepository.save(e);
    }

    public Optional<Event> findById(Long id) {
        return eventRepository.findById(id);
    }
}
