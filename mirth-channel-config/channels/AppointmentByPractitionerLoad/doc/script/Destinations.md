# AppointmentByPractitionerLoad

__Channel Export__ - Wed Jun 22 2022 16:42:39 GMT-0000 (UTC)

__Destination Scripts__
```
This channel has 3 destinations.



________________________________________________________________________________________________________________________________
Destination 1 New Patients



____________________________________________________________
Filter		



____________________________________________________________
Rule 0 filter patients		

return destinationFilter("patient");



____________________________________________________________
Connector		


return destinationWriter("patient");



________________________________________________________________________________________________________________________________
Destination 2 Conditions





____________________________________________________________
Connector		


return destinationWriter("conditions");



________________________________________________________________________________________________________________________________
Destination 3 Appointments



____________________________________________________________
Transformer



____________________________________________________________
Step 0 Destination Transform		

destinationTransform("appointments");



____________________________________________________________
Connector		


return destinationWriter("appointments");
```