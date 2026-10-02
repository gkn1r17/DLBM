function S = readContinuousBinary(filename)
%READCONTINUOUSBINARY Read a dense continuous-dLBM snapshot.
%
% S.active   : nLocation x nPopulation double
% S.dormant  : nLocation x nPopulation double (empty if dormancy disabled)
% S.hour     : model hour stored in snapshot
% S.nLoc     : number of locations
% S.nPop     : number of fixed populations
%
% Population IDs, origins, phenotypes and Topt are stored once per run in
% populations.csv. Java DataOutputStream is big-endian, hence ieee-be.

fid = fopen(filename, 'r', 'ieee-be');
if fid < 0
    error('Could not open %s', filename)
end
cleanup = onCleanup(@() fclose(fid));

magic = char(fread(fid, 7, '*uint8')');
if ~strcmp(magic, 'DLBMC01')
    error('Not a continuous dLBM binary snapshot: %s', filename)
end

S.nLoc = fread(fid, 1, 'int32=>double');
S.nPop = fread(fid, 1, 'int32=>double');
nStates = fread(fid, 1, 'uint8=>double');
S.hour = fread(fid, 1, 'int64=>double');

n = S.nLoc * S.nPop;
x = fread(fid, n, 'double=>double');
if numel(x) ~= n
    error('Unexpected end of file while reading active abundance')
end
S.active = reshape(x, [S.nPop, S.nLoc])';

if nStates == 2
    x = fread(fid, n, 'double=>double');
    if numel(x) ~= n
        error('Unexpected end of file while reading dormant abundance')
    end
    S.dormant = reshape(x, [S.nPop, S.nLoc])';
elseif nStates == 1
    S.dormant = [];
else
    error('Unknown number of states in file: %d', nStates)
end
end
