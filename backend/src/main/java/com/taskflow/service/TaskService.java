package com.taskflow.service;

import com.taskflow.dto.TaskDTO;
import com.taskflow.dto.TaskSummaryDTO;
import com.taskflow.dto.WebsocketNotificationDTO;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ForbiddenException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.*;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import java.util.*;
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
    private com.taskflow.service.TeamsService teamsService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TimeEntryService timeEntryService;

    // Get all tasks for the currently authenticated user.
    // Supports optional status and priority filters.

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','MEMBER','VIEWER')")
    public List<TaskDTO> getAllTasksForCurrentUser() {
        User user = getCurrentUser();

        // Admin can view all tasks
        if (user.getRole() == Role.ADMIN) {
            return taskRepository.findAll().stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }

        // Tasks created by user
        List<Task> all = taskRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        // Tasks assigned to user
        all.addAll(taskRepository.findByAssigneesIdOrderByCreatedAtDesc(user.getId()));

        // Tasks belonging to teams the user is part of
        List<Long> teamIds = teamsService.getTeamsForCurrentUser().stream()
                .map(t -> t.getId())
                .collect(Collectors.toList());
        if (!teamIds.isEmpty()) {
            all.addAll(taskRepository.findByTeamIdInOrderByCreatedAtDesc(teamIds));
        }

        return all.stream()
                .filter(t -> t != null && t.getId() != null)
                .collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a))
                .values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TaskDTO> getTasksByStatus(TaskStatus status) {
        return getAllTasksForCurrentUser().stream()
                .filter(task -> task.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<TaskDTO> getTasksByPriority(TaskPriority priority) {
        return getAllTasksForCurrentUser().stream()
                .filter(task -> task.getPriority() == priority)
                .collect(Collectors.toList());
    }

    public List<TaskDTO> getTasksByStatusAndPriority(TaskStatus status, TaskPriority priority) {
        return getAllTasksForCurrentUser().stream()
                .filter(task -> task.getStatus() == status && task.getPriority() == priority)
                .collect(Collectors.toList());
    }

    // F-EXT-02 TC-A04: "Assigned to Me" filter.(F-EXT-02 Means Which Features are
    // Expand in second phase of project)

    public List<TaskDTO> getTasksAssignedToMe() {
        Long userId = getCurrentUserId();
        return taskRepository.findByAssigneesIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public TaskDTO getTaskById(Long taskId) {
        Task task = getAccessibleTask(taskId);
        return toDTO(task);
    }

    /**
     * Returns the task if the current user is allowed to view it.
     * Admins can view any task; others must be owner, assignee, or member of the
     * task team.
     */
    public Task getAccessibleTask(Long taskId) {
        User user = getCurrentUser();

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        // Admin can access any task
        if (user.getRole() == Role.ADMIN) {
            return task;
        }

        boolean isOwner = task.getUser().getId().equals(user.getId());
        boolean isAssignee = task.getAssignees().stream().anyMatch(a -> a.getId().equals(user.getId()));
        boolean isTeamMember = task.getTeam() != null &&
                teamsService.getTeamsForCurrentUser().stream()
                        .anyMatch(team -> team.getId().equals(task.getTeam().getId()));
        boolean isTeamManager = task.getTeam() != null && task.getTeam().getManager() != null &&
                task.getTeam().getManager().getId().equals(user.getId());

        if (isOwner || isAssignee || isTeamMember || isTeamManager) {
            return task;
        }

        // Task exists but the user does not have access.
        throw new ForbiddenException("You do not have access to this task");
    }

    // ─── CREATE

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','MEMBER')")
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

        if (taskDTO.getTeamId() != null) {
            builder.team(teamsService.getTeamById(taskDTO.getTeamId()));
        }

        // F-EXT-02: Handle multi-assignment — only ADMIN/MANAGER can assign
        if (taskDTO.getAssigneeIds() != null && !taskDTO.getAssigneeIds().isEmpty()) {
            if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
                throw new ForbiddenException("Only Admin or Manager can assign tasks to others");
            }

            Set<User> assignees = new HashSet<>();
            for (Long assigneeId : taskDTO.getAssigneeIds()) {
                User assignee = userRepository.findById(assigneeId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + assigneeId));

                if (assignee.getRole() == Role.VIEWER) {
                    throw new BadRequestException("Cannot assign tasks to a Viewer: " + assignee.getUsername());
                }

                // If Manager is assigning, user must be in one of the manager's teams
                if (user.getRole() == Role.MANAGER) {
                    boolean inMyTeam = false;
                    if (taskDTO.getTeamId() != null) {
                        inMyTeam = teamsService.isUserInTeam(taskDTO.getTeamId(), assignee.getId());
                    } else {
                        // Manager assigning without specifying a team - check if user is in ANY of manager's teams
                        inMyTeam = teamsService.getTeamsForCurrentUser().stream()
                                .anyMatch(t -> teamsService.isUserInTeam(t.getId(), assignee.getId()));
                    }
                    
                    if (!inMyTeam && !assignee.getId().equals(user.getId())) {
                        throw new ForbiddenException("Cannot assign user " + assignee.getUsername() + " (Not in your team)");
                    }
                }
                assignees.add(assignee);
            }
            builder.assignees(assignees);
        }

        Task savedTask = taskRepository.save(builder.build());

        // Log activity
        activityLogService.log(savedTask, user,
                ActivityLogService.TASK_CREATED,
                "created task \"" + savedTask.getTitle() + "\"");

        if (!savedTask.getAssignees().isEmpty()) {
            String names = savedTask.getAssignees().stream().map(User::getUsername).collect(Collectors.joining(", "));
            activityLogService.log(savedTask, user,
                    ActivityLogService.TASK_ASSIGNED,
                    "assigned \"" + savedTask.getTitle() + "\" to " + names);
            // Notify each assignee that a task was assigned to them
            notifyAssignees(savedTask, user);
        }

        // Broadcast to team if assigned to one
        if (savedTask.getTeam() != null) {
            WebsocketNotificationDTO teamNotif = WebsocketNotificationDTO.builder()
                    .type("TASK_CREATED")
                    .timestamp(Instant.now())
                    .initiatedBy(user.getEmail())
                    .payload(Map.of(
                        "taskData", toDTO(savedTask),
                        "message", "New task created: " + savedTask.getTitle()
                    ))
                    .build();
            notificationService.notifyTeam(savedTask.getTeam().getId(), teamNotif);
        }

        return toDTO(savedTask);
    }

    // UPDATE

    @Transactional
    public TaskDTO updateTask(Long taskId, TaskDTO taskDTO) {
        User user = getCurrentUser();

        if (user.getRole() == Role.VIEWER) {
            throw new ForbiddenException("Viewers cannot modify tasks");
        }

        Task existingTask = getAccessibleTask(taskId);

        if (taskDTO.getTitle() == null || taskDTO.getTitle().isBlank()) {
            throw new BadRequestException("Task title is required");
        }

        // Track assignment change for activity log
        Set<User> oldAssignees = new HashSet<>(existingTask.getAssignees());
        TaskStatus oldStatus = existingTask.getStatus();

        // ─── PERMISSION CHECK ───
        boolean isAdminOrManager = user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER;
        boolean isCreator = existingTask.getUser() != null && existingTask.getUser().getId().equals(user.getId());
        boolean isAssignee = oldAssignees.stream().anyMatch(a -> a.getId().equals(user.getId()));

        if (!isAdminOrManager && !isCreator && !isAssignee) {
            throw new ForbiddenException("You do not have permission to update this task");
        }

        // Only Admin/Manager/Creator can change metadata (Title, Desc, DueDate, Team)
        if (!isAdminOrManager && !isCreator) {
            if (!existingTask.getTitle().equals(taskDTO.getTitle()) ||
                (existingTask.getDescription() != null && !existingTask.getDescription().equals(taskDTO.getDescription())) ||
                (existingTask.getDueDate() != null && !existingTask.getDueDate().equals(taskDTO.getDueDate()))) {
                throw new ForbiddenException("Only Admin, Manager or Creator can modify task details");
            }
        }

        existingTask.setTitle(taskDTO.getTitle().trim());
        existingTask.setDescription(taskDTO.getDescription());
        existingTask.setDueDate(taskDTO.getDueDate());

        // Optional team association - Only Admin/Manager/Creator
        if (taskDTO.getTeamId() != null) {
            if (isAdminOrManager || isCreator) {
                existingTask.setTeam(teamsService.getTeamById(taskDTO.getTeamId()));
            }
        } else if (isAdminOrManager || isCreator) {
            existingTask.setTeam(null);
        }

        if (taskDTO.getStatus() != null) {
            existingTask.setStatus(taskDTO.getStatus());
        }

        // If moved to COMPLETED, stop remaining timers for task
        if (taskDTO.getStatus() == TaskStatus.COMPLETED && oldStatus != TaskStatus.COMPLETED) {
            timeEntryService.stopActiveTimersForTask(existingTask);
        }
        if (taskDTO.getPriority() != null) {
            // Only Admin/Manager/Creator can change priority
            if (isAdminOrManager || isCreator) {
                existingTask.setPriority(taskDTO.getPriority());
            }
        }

        // F-EXT-02: Handle multi-assignment change — only ADMIN/MANAGER can assign/reassign
        if (taskDTO.getAssigneeIds() != null) {
            Set<Long> newIds = new HashSet<>(taskDTO.getAssigneeIds());
            Set<Long> oldIds = oldAssignees.stream().map(User::getId).collect(Collectors.toSet());

            if (!newIds.equals(oldIds)) {
                if (!isAdminOrManager) {
                    throw new ForbiddenException("Only Admin or Manager can reassign tasks");
                }

                Set<User> newAssignees = new HashSet<>();
                for (Long assigneeId : taskDTO.getAssigneeIds()) {
                    User assignee = userRepository.findById(assigneeId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + assigneeId));

                    if (assignee.getRole() == Role.VIEWER) {
                        throw new BadRequestException("Cannot assign tasks to a Viewer: " + assignee.getUsername());
                    }

                    if (user.getRole() == Role.MANAGER) {
                        Long checkTeamId = taskDTO.getTeamId() != null ? taskDTO.getTeamId()
                                : (existingTask.getTeam() != null ? existingTask.getTeam().getId() : null);

                        boolean inMyTeam = false;
                        if (checkTeamId != null) {
                            inMyTeam = teamsService.isUserInTeam(checkTeamId, assignee.getId());
                        } else {
                            inMyTeam = teamsService.getTeamsForCurrentUser().stream()
                                    .anyMatch(t -> teamsService.isUserInTeam(t.getId(), assignee.getId()));
                        }

                        if (!inMyTeam && !assignee.getId().equals(user.getId())) {
                            throw new ForbiddenException("Cannot assign user " + assignee.getUsername() + " (Not in your team)");
                        }
                    }
                    newAssignees.add(assignee);
                }
                existingTask.getAssignees().clear();
                existingTask.getAssignees().addAll(newAssignees);
            }
        }

        Task updatedTask = taskRepository.save(existingTask);

        // Log activity
        activityLogService.log(updatedTask, user,
                ActivityLogService.TASK_UPDATED,
                "updated task \"" + updatedTask.getTitle() + "\"");

        // Log status change specifically
        if (taskDTO.getStatus() != null && !taskDTO.getStatus().equals(oldStatus)) {
            activityLogService.log(updatedTask, user,
                    ActivityLogService.TASK_STATUS_CHANGED,
                    "changed status of \"" + updatedTask.getTitle()
                            + "\" from " + oldStatus + " to " + taskDTO.getStatus());
        }

        // Log assignment change and notify newly added assignees
        Set<User> newAssignees = updatedTask.getAssignees();
        Set<User> addedAssignees = new HashSet<>(newAssignees);
        addedAssignees.removeAll(oldAssignees);
        boolean changed = !oldAssignees.equals(newAssignees);
        if (changed && !newAssignees.isEmpty()) {
            String names = newAssignees.stream().map(User::getUsername).collect(Collectors.joining(", "));
            activityLogService.log(updatedTask, user,
                    ActivityLogService.TASK_ASSIGNED,
                    "assigned \"" + updatedTask.getTitle() + "\" to " + names);
            // Notify only newly added assignees
            if (!addedAssignees.isEmpty()) {
                notifyAssignees(updatedTask, user, addedAssignees);
            }
        }

        // If task was just marked COMPLETED → notify the task creator + all admins
        if (taskDTO.getStatus() == TaskStatus.COMPLETED && oldStatus != TaskStatus.COMPLETED) {
            notifyCompletion(updatedTask, user);
        }

        // Broadcast update to team
        if (updatedTask.getTeam() != null) {
            WebsocketNotificationDTO teamNotif = WebsocketNotificationDTO.builder()
                    .type("TASK_UPDATED")
                    .timestamp(Instant.now())
                    .initiatedBy(user.getEmail())
                    .payload(Map.of(
                        "taskData", toDTO(updatedTask),
                        "message", "Task updated: " + updatedTask.getTitle()
                    ))
                    .build();
            notificationService.notifyTeam(updatedTask.getTeam().getId(), teamNotif);
        }

        return toDTO(updatedTask);
    }

    // DELETE TASK

    @Transactional
    public void deleteTask(Long taskId) {
        User user = getCurrentUser();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found with id: " + taskId));

        // Admin check prioritized for performance and to bypass ownership evaluation
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = task.getUser() != null && task.getUser().getId().equals(user.getId());
        boolean isManagerOfTeam = task.getTeam() != null
                && task.getTeam().getManager() != null
                && task.getTeam().getManager().getId().equals(user.getId());

        if (!(isAdmin || isOwner || isManagerOfTeam)) {
            throw new ForbiddenException("Only Admin, Team Manager, or Task Owner can delete this task.");
        }

        String taskTitle = task.getTitle();

        // Notify task owner and assignees before deletion
        Map<String, Object> deletePayload = new HashMap<>();
        deletePayload.put("taskId", taskId);
        deletePayload.put("taskTitle", taskTitle);
        deletePayload.put("message", user.getUsername() + " deleted task \"" + taskTitle + "\"");
        WebsocketNotificationDTO deleteNotif = WebsocketNotificationDTO.builder()
                .type("TASK_DELETED")
                .timestamp(Instant.now())
                .initiatedBy(user.getEmail())
                .payload(deletePayload)
                .build();
        if (task.getUser() != null && !task.getUser().getId().equals(user.getId())) {
            notificationService.notifyUser(task.getUser().getEmail(), deleteNotif);
        }
        for (User assignee : task.getAssignees()) {
            if (!assignee.getId().equals(user.getId())) {
                notificationService.notifyUser(assignee.getEmail(), deleteNotif);
            }
        }

        // Broadcast delete to team
        if (task.getTeam() != null) {
            notificationService.notifyTeam(task.getTeam().getId(), deleteNotif);
        }

        // Clear FK references before deleting the task
        commentRepository.deleteByTaskId(taskId);
        activityLogRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);

        // Log with null task (task is deleted)
        activityLogService.log(user,
                ActivityLogService.TASK_DELETED,
                "deleted task \"" + taskTitle + "\"");
    }

    // ─── F-EXT-04: ANALYTICS / SUMMARY (F-EXT-04 Means Which Features are Expand
    // in second phase of project)

    public TaskSummaryDTO getTaskSummary() {
        LocalDate today = LocalDate.now();

        List<TaskDTO> tasks = getAllTasksForCurrentUser();
        long total = tasks.size();
        long completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long pending = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long overdue = tasks.stream().filter(
                t -> t.getDueDate() != null && t.getDueDate().isBefore(today) && t.getStatus() != TaskStatus.COMPLETED)
                .count();
        double rate = total > 0 ? Math.round((completed * 100.0 / total) * 10) / 10.0 : 0;

        long highPriority = tasks.stream().filter(t -> t.getPriority() == TaskPriority.HIGH).count();
        long medPriority = tasks.stream().filter(t -> t.getPriority() == TaskPriority.MEDIUM).count();
        long lowPriority = tasks.stream().filter(t -> t.getPriority() == TaskPriority.LOW).count();

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

    /**
     * Notify each assignee (excluding the assigner) that a task has been assigned to them.
     * Overload for all current assignees (used on create).
     */
    private void notifyAssignees(Task task, User assigner) {
        notifyAssignees(task, assigner, task.getAssignees());
    }

    /**
     * Notify a specific set of assignees (used when only new assignees are added on update).
     */
    private void notifyAssignees(Task task, User assigner, Set<User> targets) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getId());
        payload.put("taskTitle", task.getTitle());
        payload.put("assignedBy", assigner.getUsername());
        payload.put("message", assigner.getUsername() + " assigned you to task \"" + task.getTitle() + "\"");
        if (task.getTeam() != null) {
            payload.put("teamId", task.getTeam().getId());
            payload.put("teamName", task.getTeam().getName());
        }

        WebsocketNotificationDTO notification = WebsocketNotificationDTO.builder()
                .type("TASK_ASSIGNED")
                .timestamp(Instant.now())
                .payload(payload)
                .build();

        for (User assignee : targets) {
            // Don't notify the assigner if they assigned themselves
            if (!assignee.getId().equals(assigner.getId())) {
                notificationService.notifyUser(assignee.getEmail(), notification);
            }
        }
    }

    /**
     * When a task is marked COMPLETED, notify:
     *  - the task creator (the one who assigned/created it)
     *  - all ADMIN users in the system
     * The person who completed it (current user) is excluded to avoid self-notification.
     */
    private void notifyCompletion(Task task, User completedBy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getId());
        payload.put("taskTitle", task.getTitle());
        payload.put("completedBy", completedBy.getUsername());
        payload.put("message", completedBy.getUsername() + " completed task \"" + task.getTitle() + "\"");
        if (task.getTeam() != null) {
            payload.put("teamId", task.getTeam().getId());
            payload.put("teamName", task.getTeam().getName());
        }

        WebsocketNotificationDTO notification = WebsocketNotificationDTO.builder()
                .type("TASK_COMPLETED")
                .timestamp(Instant.now())
                .payload(payload)
                .build();

        // Notify the task creator (unless they are the one who completed it)
        if (task.getUser() != null && !task.getUser().getId().equals(completedBy.getId())) {
            notificationService.notifyUser(task.getUser().getEmail(), notification);
        }

        // Notify all ADMINs (unless admin is the one who completed it)
        List<User> admins = userRepository.findByRoleIn(List.of(Role.ADMIN));
        for (User admin : admins) {
            if (!admin.getId().equals(completedBy.getId())) {
                notificationService.notifyUser(admin.getEmail(), notification);
            }
        }
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
                .assigneeIds(task.getAssignees().stream().map(User::getId).collect(Collectors.toList()))
                .assigneeNames(task.getAssignees().stream().map(User::getUsername).collect(Collectors.toList()))
                .userId(task.getUser() != null ? task.getUser().getId() : null)
                .userName(task.getUser() != null ? task.getUser().getUsername() : null)
                .teamId(task.getTeam() != null ? task.getTeam().getId() : null)
                .teamName(task.getTeam() != null ? task.getTeam().getName() : null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .overdue(isOverdue)
                .build();
    }
}
