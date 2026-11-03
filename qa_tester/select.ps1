param([Parameter(Mandatory)][string]$Sql)
if($Sql -match '(?i)\b(UPDATE|INSERT|DELETE|DROP|TRUNCATE|ALTER|CREATE|EXEC|MERGE|BACKUP|RESTORE)\b'){throw 'SELECT only'}
. (Join-Path $PSScriptRoot 'db-common.ps1')
try{$cmd=$connection.CreateCommand();$cmd.CommandText=$Sql;$reader=$cmd.ExecuteReader();$sets=@();do{$rows=@();while($reader.Read()){$row=@{};for($i=0;$i -lt $reader.FieldCount;$i++){$row[$reader.GetName($i)]=if($reader.IsDBNull($i)){$null}else{$reader.GetValue($i)}};$rows+=$row};$sets+=,@($rows)}while($reader.NextResult());$sets|ConvertTo-Json -Depth 6}finally{$connection.Dispose()}
