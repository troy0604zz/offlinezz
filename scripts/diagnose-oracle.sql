set pagesize 200
set linesize 220
set feedback on
set verify off
column name format a20
column open_mode format a20
column restricted format a12
column username format a20
column status format a10
column event format a45
select name, open_mode, restricted from v$pdbs order by con_id;
select username, status, event, seconds_in_wait from v$session
where username is not null and type = 'USER'
order by logon_time;
select comp_id, comp_name, status from dba_registry order by comp_id;
exit
