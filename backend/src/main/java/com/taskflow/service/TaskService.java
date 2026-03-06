package com.taskflow.service;

import com.taskflow.dto.TaskDTO;
import com.taskflow.dto.TaskSummaryDTO;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ForbiddenException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.taskflow.repository.TaskCommentRepository commentRepository;

    @Autowired
    private com.taskflow.repository.ActivityLogRepository activityLogRepository;

    @Autowired
    private ActivityLogService activityLogService;


     //Get all tasks for the currently authenticated user.
     // Supports optional status and priority filters.
    
    public List<TaskDTO> getAllTasksForCurrentUser() {
        Long userId = getCurrentUserId();
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TaskDTO> getTasksByStatus(TaskStatus status) {
        Long userId = getCurrentUserId();
        return taskRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TaskDTO> getTasksByPriority(TaskPriority priority) {
        Long userId = getCurrentUserId();
        return taskRepository.findByUserIdAndPriorityOrderByCreatedAtDesc(userId, priority)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TaskDTO> getTasksByStatusAndPriority(TaskStatus status, TaskPriority priority) {
        Long userId = getCurrentUserId();
        return taskRepository.findByUserIdAndStatusAndPriorityOrderByCreatedAtDesc(userId, status, priority)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    
     // F-EXT-02 TC-A04: "Assigned to Me" filter.(F-EXT-02 Means Which Features are Expand in second phase of project)
    
    public List<TaskDTO> getTasksAssignedToMe() {
        Long userId = getCurrentUserId();
        return taskRepository.findByAssignedToIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public TaskDTO getTaskById(Long taskId) {
        Long userId = getCurrentUserId();
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found with id: " + taskId));
        return toDTO(task);
    }

    // ─── CREATE 

    @Transactional
    public TaskDTO createTask(TaskDTO taskDTO) {
        User user = getCurrentUser();

        if (taskDTO.getTitle() == null || taskDTO.getTitle().isBlank()) {
            throw new BadRequestException("Task title is required");
        }

        Task.TaskBuilder builder = Task.builder()
                .title(taskDTO.getTitle().trim())
                .description(taskDTO.getDescription())
                .status(taskDTO.getStatus() != null ? taskDTO.getStatus() : TaskStatus.PENDING)
                .priority(taskDTO.getPriority() != null ? taskDTO.getPriority() : TaskPriority.MEDIUM)
                .dueDate(taskDTO.getDueDate())
                .user(user);

        // F-EXT-02: Handle assignment — only ADMIN/MANAGER can assign
        if (taskDTO.getAssignedToId() != null) {
            if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
                throw new ForbiddenException("Only Admin or Manager can assign tasks");
            }
            User assignee = userRepository.findById(taskDTO.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Assignee user not found with id: " + taskDTO.getAssignedToId()));
            // Assignee must be DEVELOPER or TESTER
            if (assignee.getRole() != Role.DEVELOPER && assignee.getRole() != Role.TESTER) {
                throw new BadRequestException("Tasks can only be assigned to Developer or Tester");
            }
            builder.assignedTo(assignee);
        }

        Task savedTask = taskRepository.save(builder.build());

        // Log activity
        activityLogService.log(savedTask, user,
                ActivityLogService.TASK_CREATED,
                user.getUsername() + " created task \"" + savedTask.getTitle() + "\"");

        if (savedTask.getAssignedTo() != null) {
            activityLogService.log(savedTask, user,
                    ActivityLogService.TASK_ASSIGNED,
                    user.getUsername() + " assigned \"" + savedTask.getTitle()
                            + "\" to " + savedTask.getAssignedTo().getUsername());
        }

        return toDTO(savedTask);
    }

    //  UPDATE 

    @Transactional
    public TaskDTO updateTask(Long taskId, TaskDTO taskDTO) {
        User user = getCurrentUser();
        Task existingTask = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found with id: " + taskId));

        if (taskDTO.getTitle() == null || taskDTO.getTitle().isBlank()) {
            throw new BadRequestException("Task title is required");
        }

        // Track status change for activity log
        TaskStatus oldStatus = existingTask.getStatus();
        User oldAssignee = existingTask.getAssignedTo();

        existingTask.setTitle(taskDTO.getTitle().trim());
        existingTask.setDescription(taskDTO.getDescription());
        existingTask.setDueDate(taskDTO.getDueDate());

        if (taskDTO.getStatus() != null) {
            existingTask.setStatus(taskDTO.getStatus());
        }
        if (taskDTO.getPriority() != null) {
            existingTask.setPriority(taskDTO.getPriority());
        }

        // F-EXT-02: Handle assignment change — only ADMIN/MANAGER can assign
        if (taskDTO.getAssignedToId() != null) {
            if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
                throw new ForbiddenException("Only Admin or Manager can assign tasks");
            }
            User assignee = userRepository.findById(taskDTO.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Assignee user not found with id: " + taskDTO.getAssignedToId()));
            if (assignee.getRole() != Role.DEVELOPER && assignee.getRole() != Role.TESTER) {
                throw new BadRequestException("Tasks can only be assigned to Developer or Tester");
            }
            existingTask.setAssignedTo(assignee);
        } else {
            existingTask.setAssignedTo(null);
        }

        Task updatedTask = taskRepository.save(existingTask);

        // Log activity
        activityLogService.log(updatedTask, user,
                ActivityLogService.TASK_UPDATED,
                user.getUsername() + " updated task \"" + updatedTask.getTitle() + "\"");

        // Log status change specifically
        if (taskDTO.getStatus() != null && !taskDTO.getStatus().equals(oldStatus)) {
            activityLogService.log(updatedTask, user,
                    ActivityLogService.TASK_STATUS_CHANGED,
                    user.getUsername() + " changed status of \"" + updatedTask.getTitle()
                            + "\" from " + oldStatus + " to " + taskDTO.getStatus());
        }

        // Log assignment change
        User newAssignee = updatedTask.getAssignedTo();
        boolean assigneeChanged = (oldAssignee == null && newAssignee != null)
                || (oldAssignee != null && newAssignee == null)
                || (oldAssignee != null && newAssignee != null
                    && !oldAssignee.getId().equals(newAssignee.getId()));
        if (assigneeChanged && newAssignee != null) {
            activityLogService.log(updatedTask, user,
                    ActivityLogService.TASK_ASSIGNED,
                    user.getUsername() + " assigned \"" + updatedTask.getTitle()
                            + "\" to " + newAssignee.getUsername());
        }

        return toDTO(updatedTask);
    }

    // DELETE  TASK

    @Transactional
    public void deleteTask(Long taskId) {
        User user = getCurrentUser();
        Task task = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found with id: " + taskId));

        String taskTitle = task.getTitle();

        // Clear FK references before deleting the task
        commentRepository.deleteByTaskId(taskId);
        activityLogRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);

        // Log with null task (task is deleted)
        activityLogService.log(user,
                ActivityLogService.TASK_DELETED,
                user.getUsername() + " deleted task \"" + taskTitle + "\"");
    }

    // ─── F-EXT-04: ANALYTICS / SUMMARY (F-EXT-04 Means Which Features are Expand in second phase of project)

    public TaskSummaryDTO getTaskSummary() {
        Long userId = getCurrentUserId();
        LocalDate today = LocalDate.now();

        long total = taskRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
        long completed = taskRepository.countByUserIdAndStatus(userId, TaskStatus.COMPLETED);
        long pending = taskRepository.countByUserIdAndStatus(userId, TaskStatus.PENDING);
        long inProgress = taskRepository.countByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        long overdue = taskRepository
                .findByUserIdAndDueDateBeforeAndStatusNot(userId, today, TaskStatus.COMPLETED)
                .size();
        double rate = total > 0 ? Math.round((completed * 100.0 / total) * 10) / 10.0 : 0;

        long highPriority = taskRepository.countByUserIdAndPriority(userId, TaskPriority.HIGH);
        long medPriority = taskRepository.countByUserIdAndPriority(userId, TaskPriority.MEDIUM);
        long lowPriority = taskRepository.countByUserIdAndPriority(userId, TaskPriority.LOW);

        return TaskSummaryDTO.builder()
                .totalTasks(total)
                .completedTasks(completed)
                .pendingTasks(pending)
                .inProgressTasks(inProgress)
                .overdueTasks(overdue)
                .completionRate(rate)
                .highPriorityTasks(highPriority)
                .mediumPriorityTasks(medPriority)
                .lowPriorityTasks(lowPriority)
                .build();
    }

    // Helper Methods

    private Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private TaskDTO toDTO(Task task) {
        LocalDate today = LocalDate.now();
        boolean isOverdue = task.getDueDate() != null
                && task.getDueDate().isBefore(today)
                && task.getStatus() != TaskStatus.COMPLETED;

        return TaskDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .assignedToId(task.getAssignedTo() != null ? task.getAssignedTo().getId() : null)
                .assignedToName(task.getAssignedTo() != null ? task.getAssignedTo().getUsername() : null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .overdue(isOverdue)
                .build();
    }
}
