function [diversity,limits] = loadTSLog(dirname, fname, numBoxes, justHead)
%Loads CSV diversity/richness file: called by convert files

    limits = nan(1,numBoxes);
    locals = [];
    globals = [];
    mutants = [];
    tsDays = [];
    tsDaysM = [];


    for n = 0:6
        filename = dirname + "/" + fname + "_N" + n + ".csv";
        
        if isfile(filename)

            fid = fopen(dirname + "/" + fname + "_N" + n + ".csv");
            fLine = fgetl(fid); 
            nodeIDs = str2double(strsplit(fLine, ","));
	    nodeIDs = nodeIDs(1,4:end); 
            nodeIDs = nodeIDs(~isnan(nodeIDs));
	    fLine = fgetl(fid); 
            lim = str2double(strsplit(fLine, ","));
            lim = lim(1,4:end);
            lim = lim(~isnan(lim));
            limits(1, nodeIDs + 1) = lim;

	  if ~justHead


            tsLogData = readmatrix(dirname + "/" + fname + "_N" + n + ".csv", 'NumHeaderLines', 0, 'OutputType','string');
            localIdx = find(tsLogData(:,1) == "day");
            globalIdx = find(tsLogData(:,1) == "global");
            mutantIdx = find(tsLogData(:,1) == "day(mutantIDs)");


            tsLogData = readmatrix(dirname + "/" + fname + "_N" + n + ".csv", 'NumHeaderLines', 0, 'OutputType','double');
            

            if(~isempty(mutantIdx))
                if isempty(mutants)
                    tsDaysM = tsLogData(mutantIdx,2);
                    mutants = nan(numel(tsDaysM), numBoxes);
                end
                numDays = 1:min(size(mutantIdx, 1), numel(tsDaysM)) ;
                mutMat = tsLogData(mutantIdx(numDays),5:end);
                mutMat = mutMat(:,~isnan(mutMat(1,:))); 
                mutants(numDays, nodeIDs + 1) = mutMat;
            end

%% %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% UNCOMMENT TO SAVE diversityTS.locals and diversityTS.globals (diversity timesteps more fine grained than avalable in data).
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% !!!!SPACE INTENSIVE
%            if(isempty(locals))
%                tsDays = tsLogData(localIdx,2);
%                locals = nan(numel(tsDays) ,numBoxes);
%            end

%            numDays = 1:min(size(localIdx, 1), numel(tsDays)) ;
%            locals(numDays, nodeIDs + 1) = tsLogData(localIdx(numDays),7:end);
                
%            if(~isempty(globalIdx))
%                globals = tsLogData(globalIdx,2);
%            end


	  end
        end
        
    end

    [~, b] = unique(tsDaysM)

    tsDaysM = tsDaysM(b');
    mutants = mutants(b',:); 

    diversity = struct();
    diversity.globals = []; %globals;
    diversity.locals = []; %locals;
    diversity.days = tsDays;
    diversity.daysM = tsDaysM;
    diversity.mutants = mutants;


end
