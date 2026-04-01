### REQUEST
 
```
curl --request POST \
     --url https://api.followupboss.com/v1/tasks \
     --header 'accept: application/json' \
     --header 'authorization: Basic ZmthXzAwTVIxcGlpd3E5eXN1ejY3WTdJUncxaEQ2a1dKODdNY3I6' \
     --header 'content-type: application/json' \
     --data '
{
  "personId": 18399,
  "name": "Task Creation",
  "assignedUserId": 18399
}

```

### RESPONSE

{
  "id": 31062,
  "created": "2026-03-11T16:54:05Z",
  "updated": "2026-03-11T16:54:05Z",
  "completed": null,
  "createdById": 30,
  "updatedById": 30,
  "createdBy": "Sarath Kumar",
  "updatedBy": "Sarath Kumar",
  "personId": 18399,
  "AssignedTo": null,
  "assignedUserId": 18399,
  "name": "Task Creation",
  "type": "Follow Up",
  "isCompleted": 0,
  "dueDate": null,
  "externalTaskLink": null,
  "externalCalendarId": null,
  "remindSecondsBefore": null,
  "dueDateTime": null
}