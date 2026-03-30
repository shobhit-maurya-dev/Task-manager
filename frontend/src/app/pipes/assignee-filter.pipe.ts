import { Pipe, PipeTransform } from '@angular/core';
import { Task } from '../models/task.model';

/**
 * AssigneeFilterPipe — filters tasks where task.assignedTo === given userId.
 * Usage: *ngFor="let task of tasks | assigneeFilter:currentUserId"
 */
@Pipe({
  name: 'assigneeFilter',
  standalone: true
})
export class AssigneeFilterPipe implements PipeTransform {
  transform(tasks: Task[], userId: number | null): Task[] {
    if (!userId || !tasks) return tasks;
    return tasks.filter(t => t.assigneeIds?.includes(userId));
  }
}
