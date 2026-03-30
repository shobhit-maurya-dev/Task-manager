export interface Team {
  id?: number;
  name: string;
  description?: string;
  managerId?: number;
  managerName?: string;
  memberCount?: number;
  activeTaskCount?: number;
  access?: 'Private' | 'Public' | string;
  role?: 'ADMIN' | 'MANAGER' | 'MEMBER' | 'VIEWER' | string;
  members?: TeamMember[]; 
  tasks?: TeamTask[];
  createdAt?: string;
}

export interface TeamDetail extends Team {
  members?: TeamMember[];
  tasks?: TeamTask[];
}

export interface TeamMember {
  id: number;
  userId?: number; // optional for fixture objects and backward compatibility
  username: string;
  email?: string;
  role?: string;
  memberType?: string;
  isLeader?: boolean;
  joinedAt?: string;
  tasksAssigned?: number;
}

export interface TeamTask {
  taskId: number;
  title: string;
  status: string;
}
