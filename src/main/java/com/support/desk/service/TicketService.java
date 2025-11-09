package com.support.desk.service;

import com.support.desk.dto.TicketCommentDTO;
import com.support.desk.dto.TicketDTO;
import com.support.desk.dto.TicketDetailsUpdateDTO;
import com.support.desk.dto.TicketEmpDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.support.desk.exception.ResourceNotFoundException;
import com.support.desk.model.*;
import com.support.desk.repository.TicketCommentRepository;
import com.support.desk.repository.TicketRepository;
import com.support.desk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class TicketService {
    private static final Logger logger = LogManager.getLogger(TicketService.class);

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Transactional
    public TicketDTO createTicket(TicketDTO ticketDTO, Long userId) {
        logger.info("Creating new ticket for user ID: {}", userId);
        User customer = userRepository.findById(userId).get();
        Ticket ticket = new Ticket();
        Long ticketId = generateFourDigitNumber();
        logger.debug("Generated ticket ID: {}", ticketId);
        ticket.setTicketId(ticketId);
        ticket.setTitle(ticketDTO.getTitle());
        ticket.setDescription(ticketDTO.getDescription());
        ticket.setPriority(TicketPriority.LOW);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCustomer(customer);
        ticket.setCreationTime(LocalDateTime.now());
        ticket.setResolutionTime(LocalDateTime.now().plusHours(48));
        Ticket savedTicket = ticketRepository.save(ticket);
        return convertToDTO(savedTicket);
    }

    @Transactional
    public TicketDTO assignTicket(Long ticketId, Long agentId) {
        logger.info("Assigning ticket {} to agent {}", ticketId, agentId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> {
                    logger.error("Ticket not found with id: {}", ticketId);
                    return new ResourceNotFoundException("Ticket not found with id: " + ticketId);
                });

        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> {
                    logger.error("Agent not found with id: {}", agentId);
                    return new ResourceNotFoundException("Agent not found with id: " + agentId);
                });
        ticket.setAssignedAgent(agent);
        ticket.setDepartment(agent.getDepartment());
        Ticket updatedTicket = ticketRepository.save(ticket);
        return convertToDTO(updatedTicket);
    }

    @Transactional
    public String updateTicket(TicketDetailsUpdateDTO ticketDetailsUpdateDTO) {
        logger.info("Updating ticket with ID: {}", ticketDetailsUpdateDTO.getTicketId());
        Ticket ticket = ticketRepository.findByTicketId(ticketDetailsUpdateDTO.getTicketId());
        if (ticket == null) {
            logger.error("Ticket not found with ID: {}", ticketDetailsUpdateDTO.getTicketId());
            throw new ResourceNotFoundException("Ticket not found with ID: " + ticketDetailsUpdateDTO.getTicketId());
        }

        if (!ticket.getStatus().equals(TicketStatus.RESOLVED)) {
            if (ticketDetailsUpdateDTO.getStatus() != ticket.getStatus()
                    && ticketDetailsUpdateDTO.getStatus() != null) {
                ticket.setStatus(ticketDetailsUpdateDTO.getStatus());
                if (ticket.getStatus().equals(TicketStatus.RESOLVED)) {
                    ticket.setResolutionTime(LocalDateTime.now());
                }
            }
            if (!ticketDetailsUpdateDTO.getContent().isEmpty()) {
                TicketComment ticketComment = new TicketComment();
                ticketComment.setTicket(ticket);
                ticketComment.setUser(ticket.getCustomer());
                ticketComment.setContent(ticketDetailsUpdateDTO.getContent());
                ticketComment.setCreatedAt(LocalDateTime.now());
                ticket.getComments().add(ticketComment);
            }
            if (ticketDetailsUpdateDTO.getPriority() != ticket.getPriority()
                    && ticketDetailsUpdateDTO.getPriority() != null) {
                ticket.setPriority(ticketDetailsUpdateDTO.getPriority());
            }
            ticketRepository.save(ticket);
            return "Ticket details updated successfully";
        } else
            return "The Ticket with id " + ticket.getTicketId() + " is already Resolved.";
    }

    public List<TicketDTO> getTicketsAssociatedToCustomer(Long userId) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: "));

        List<Ticket> tickets = ticketRepository.findByCustomer(customer);
        return tickets.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<TicketEmpDTO> getTicketsByAgent(Long userId) {
        List<Ticket> tickets = ticketRepository.findByAssignedAgent(userRepository.findById(userId).get());
        return tickets.stream().map(this::convertToDTOs).collect(Collectors.toList());
    }

    public List<TicketDTO> getTicketsByStatus(TicketStatus status) {
        List<Ticket> tickets = ticketRepository.findByStatus(status);
        return tickets.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<TicketDTO> getTicketsByDepartment(String department) {
        List<Ticket> tickets = ticketRepository.findByDepartment(department);
        return tickets.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public Long getTotalActiveTicketCount() {
        Integer size = ticketRepository.findByStatus(TicketStatus.OPEN).size();
        return size.longValue();
    }

    public List<TicketCommentDTO> getCommentsByTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId);
        List<TicketComment> comments = ticketCommentRepository.findByTicketOrderByCreatedAtAsc(ticket);
        return comments.stream().map(this::convertToCommentDTO).collect(Collectors.toList());
    }

    private TicketDTO convertToDTO(Ticket ticket) {
        TicketDTO dto = new TicketDTO();
        dto.setTicketId(ticket.getTicketId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setCreationTime(ticket.getCreationTime());
        dto.setResolutionTime(ticket.getResolutionTime());
        dto.setAssignedAgent(ticket.getAssignedAgent());
        dto.setStatus(ticket.getStatus());
        dto.setComments(ticket.getComments());
        return dto;
    }

    private TicketEmpDTO convertToDTOs(Ticket ticket) {
        TicketEmpDTO dto = new TicketEmpDTO();
        dto.setTicketId(ticket.getTicketId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setCreationTime(ticket.getCreationTime());
        dto.setResolutionTime(ticket.getResolutionTime());
        dto.setPriority(ticket.getPriority());
        dto.setStatus(ticket.getStatus());
        dto.setCustomer(ticket.getCustomer());
        dto.setComments(ticket.getComments());
        return dto;
    }

    private TicketCommentDTO convertToCommentDTO(TicketComment comment) {
        TicketCommentDTO dto = new TicketCommentDTO();
        dto.setContent(comment.getContent());
        dto.setCreatedAt(comment.getCreatedAt());
        return dto;
    }

    public static Long generateFourDigitNumber() {
        Random random = new Random();
        return 1000 + random.nextLong(9000); // generates a number between 1000 and 9999
    }

}